# RAN Advisor — AI Anomaly Detection for Telco RAN: Research Summary

> Literature scan of the main **issues** and main **solutions** for AI/ML and LLM-agent
> based anomaly detection in the telecom Radio Access Network (RAN), with a focus on the
> call-drop / KPI-degradation problem this repo's drop-rate agent targets.
> Research conducted July 2026. ~40 papers, surveys, patents, and industry sources reviewed.
> Sources are linked inline and collected in [§9 References](#9-references).

---

## 1. TL;DR

- **The field is real and active.** Anomaly detection is one of the flagship AI use cases for
  5G/O-RAN alongside predictive maintenance, energy optimization, and traffic management. Both
  classical ML (Decision Tree / Random Forest / SVM / kNN / K-means) and deep learning
  (LSTM, 1D-CNN, ConvLSTM, autoencoders, VAEs, GANs, transformers) are widely applied to RAN
  KPIs such as drop rate, accessibility, retainability, and handover success.
- **The hard problems are not the models — they're the data and the operations.** Across the
  literature the same six pain points recur: (1) scarce/confidential data and missing labels,
  (2) severe class imbalance, (3) concept drift, (4) false positives → alarm fatigue,
  (5) black-box opacity blocking operator trust and root-cause analysis, and
  (6) real-time inference + model-lifecycle constraints inside the O-RAN RIC.
- **Evaluation is quietly broken.** A well-cited body of work argues current time-series
  anomaly-detection benchmarks and metrics are flawed and overstate progress — directly
  relevant to how we claim our agent "works."
- **LLM agents are the new frontier — and re-introduce old risks.** LLM/agentic RAN systems
  (e.g., Deutsche Telekom's live *RAN Guardian*) promise minutes-not-hours triage, but bring
  hallucination, weak domain grounding, unsafe tool use, and immature evaluation. The
  consensus mitigations — **RAG grounding, guardrails, tool specs, and eval harnesses** — are
  exactly the pieces this repo already builds. See [§7](#7-implications-for-ran-advisor).

---

## 2. Scope and method

"AI anomaly detection agent for telco RAN" spans three overlapping literatures:

1. **Statistical / ML anomaly detection on RAN & cellular KPIs** (SON self-healing, cell-outage
   detection, KPI monitoring).
2. **Deep / spatio-temporal / graph learning** for detection *and* root-cause analysis.
3. **LLM & agentic operations** (RAG over 3GPP/O-RAN specs, tool-calling agents, intent-based
   networking) — the category our drop-rate agent belongs to.

Searches spanned surveys, arXiv preprints, IEEE/Springer/Elsevier articles, O-RAN Alliance /
xApp-rApp literature, benchmark papers, and industry announcements. Publisher full texts were
often gated; where a full text was unreachable, findings are drawn from abstracts and
indexed summaries and are attributed conservatively.

---

## 3. Background: what "anomaly" means in the RAN

The RAN is the cell-site edge of a mobile network. Operators watch per-cell **KPIs** —
accessibility (RRC/setup success), retainability (**call/session drop rate**), mobility
(handover success), throughput, availability, and PRB utilization. An *anomaly* is a
statistically or operationally significant deviation in one or more of these signals that
signals degradation or imminent failure.

Three framings appear repeatedly:

- **SON self-healing** — cell-outage detection and compensation, historically the canonical
  anomaly-detection task; ultra-dense 5G deployments make it harder and more critical
  ([SON ML survey](https://www.researchgate.net/publication/318476403)).
- **O-RAN closed loops** — anomaly detection deployed as **xApps** (near-real-time RIC) and
  **rApps** (non-real-time RIC) for scheduling, slicing, and security
  ([xApps survey](https://mentis.info/wp-content/uploads/2025/02/xApps_COMNET_2025.pdf)).
- **Drop-rate / call-retention analytics** — the specific KPI this repo's agent reasons over,
  e.g., interpretable encoding of *Call Drop Rate*
  ([VAE concepts paper](https://arxiv.org/abs/2306.15938)) and CDR-based detection
  ([K-means on Call Detail Records](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10974756/)).

---

## 4. Main issues (the core of the ask)

The recurring, cross-paper failure modes. Each is a real reason RAN anomaly-detection systems
underperform or fail to reach production.

### 4.1 Data scarcity, confidentiality, and integration
Acquiring labeled data from a *live* 5G network is hard: confidentiality of subscriber/network
data, risk of disturbing a production network, and the difficulty of *repeating* scenarios all
limit dataset creation ([Boosted-LSTM 5G RAN logs](https://pmc.ncbi.nlm.nih.gov/articles/PMC7256370/)).
O-RAN adds interoperability with **legacy OSS/NMS**, big-data volume, and the need for heavy
data-acquisition/ETL pipelines ([ML on 5G O-RAN](https://www.sciencedirect.com/science/article/pii/S1877050923009110)).

### 4.2 Label scarcity → unsupervised by necessity
Ground-truth anomaly labels are rare and expensive; most SON anomaly-detection algorithms are
therefore **unsupervised** (with some semi-/self-supervised work), because you cannot assume
labels or even a known catalogue of anomaly types
([SON: conventional vs contemporary ML](https://www.researchgate.net/publication/361236168);
[self-supervised change detection in RAN](https://arxiv.org/pdf/2302.02025)).

### 4.3 Class imbalance
"Normal" vastly outnumbers "anomalous." Models struggle to learn what an anomaly *is* from so
few examples, and imbalance biases decision boundaries and even breaks drift detectors
([imbalanced drift streams](https://arxiv.org/pdf/2104.10228)).

### 4.4 Concept drift (non-stationarity)
Networks change — new sites, retunes, traffic shifts, software upgrades — so yesterday's
"normal" is today's false alarm. Concept drift is a leading cause of **high false-positive
rates in long-running detectors**, and imbalance makes drift itself harder to detect
([adaptive AD under drift](https://arxiv.org/html/2506.15831);
[are drift detectors reliable alarms?](https://arxiv.org/pdf/2211.13098)).

### 4.5 False positives and alarm fatigue
Low precision floods operators with alerts and destroys trust; maximizing precision to avoid
**alert fatigue** is repeatedly called out as the practical gating metric for adoption
([industry AD best practices](https://www.eyer.ai/blog/data-anomaly-detection-at-scale-best-practices/)).

### 4.6 Black-box opacity → no trust, no root cause
Many models are black boxes with little insight into *why* they flagged something. In
mission-critical operations this blocks adoption: operators need justifications to exclude
false positives, find the actual cause, and support post-incident audit
([XAI for RCA](https://www.researchgate.net/publication/400182766);
[XAI in network AD](https://easychair.org/publications/preprint/XBBlj)).
Detection alone is insufficient — operators need **root-cause analysis (RCA)**, not just a flag.

### 4.7 Spatio-temporal complexity
Faults propagate across neighboring cells and over time. Models that ignore the **spatio-temporal
correlation among failure events** (common in LTE-era solutions) miss the structure that makes
5G RCA tractable ([GNN+Transformer RCA / TRACTOR](https://arxiv.org/abs/2406.15638)).

### 4.8 Real-time inference and model-lifecycle (MLOps) constraints
Near-RT RIC control loops demand low-latency inference (sub-second, down to ~10 ms class),
forcing model compression; and production systems need explicit decisions on **retraining
frequency (a function of drift) and inference latency budgets**
([open challenges, industry view](https://arxiv.org/html/2502.05392v1)).

### 4.9 Evaluation and benchmarking are flawed
A widely cited critique (Wu & Keogh) argues **current time-series anomaly-detection benchmarks
are flawed and create an "illusion of progress"** — mislabeled ground truth, trivial anomalies,
unrealistic anomaly density, run-to-failure bias. Popular metrics (point-adjusted F1,
F1-affiliation) can **inflate scores, sometimes rating random predictions highest**
([flawed benchmarks](http://ieeexplore.ieee.org/iel7/69/10036334/09537291.pdf);
[TSAD evaluation](https://timeeval.github.io/evaluation-paper/)).

### 4.10 LLM/agent-specific issues
Bringing LLM agents into RAN ops re-introduces distinct risks: **hallucination / factuality**,
weak **domain grounding** (models don't know 3GPP/O-RAN specifics from weights), **unsafe or
hallucinated tool calls**, immature **evaluation**, and a lack of **standardization** for agent
tool specs, RAN-specific fine-tuning, and validation frameworks
([LLM network-mgmt survey](https://onlinelibrary.wiley.com/doi/full/10.1002/nem.70029);
[agentic RAN standardization](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12321753/);
[TelAgentBench](https://arxiv.org/html/2604.06209)).

### 4.11 Security and privacy
Centralizing telemetry for training raises privacy/confidentiality concerns, and the O-RAN
attack surface (xApps, open interfaces) motivates zero-trust and privacy-preserving designs
([Zero-Trust + FL for 6G O-RAN](https://www.mdpi.com/1999-5903/17/6/233)).

**Issue → why it hurts → common mitigation (at a glance):**

| # | Issue | Why it hurts | Common mitigation(s) |
|---|-------|--------------|----------------------|
| 1 | Data scarcity / confidentiality | Can't train or validate | Synthetic data, **digital twins**, transfer learning, federated learning |
| 2 | Label scarcity | Supervised learning infeasible | **Unsupervised / self-supervised**, autoencoders, automatic labeling |
| 3 | Class imbalance | Model can't learn "anomaly" | GAN/oversampling, one-class & reconstruction methods, precision-weighted loss |
| 4 | Concept drift | Silent decay, FP storms | Drift detection, adaptive/continual learning, scheduled retraining |
| 5 | False positives / alarm fatigue | Operators stop trusting it | Precision-first thresholds, ensembling, human-in-the-loop |
| 6 | Black-box opacity | No trust, no RCA | **XAI** (SHAP/concepts/causal), RCA graphs |
| 7 | Spatio-temporal structure | Misses correlated faults | **GNN + Transformer**, ConvLSTM |
| 8 | Real-time + MLOps | Can't meet RIC latency / decays | Model compression, xApp/rApp placement, retraining pipelines |
| 9 | Flawed evaluation | "Illusion of progress" | Better benchmarks/metrics, held-out live trials, eval harnesses |
| 10 | LLM hallucination / grounding / tools | Wrong or unsafe answers | **RAG**, **guardrails**, tool specs, fine-tuning, eval-benchmarks |
| 11 | Security / privacy | Data can't leave, open attack surface | **Federated learning**, zero-trust, digital-twin sandboxes |

---

## 5. Main solutions (approaches surveyed)

### 5.1 Classical ML and statistical baselines
Decision Tree, Random Forest, SVM, MLP, kNN classify KPI vectors as anomalous/normal on 5G
O-RAN datasets ([ML on 5G O-RAN](https://www.sciencedirect.com/science/article/pii/S1877050923009110));
**K-means on Call Detail Records** reportedly reached ~96% accuracy for large-scale detection
([CDR K-means](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10974756/)); ensembles combine 1D-CNN/
MLP/kNN for load prediction with DT/RF/SVM for anomaly classification
([ensemble load+AD in 5G](https://www.sciencedirect.com/science/article/abs/pii/S0140366422004054)).

### 5.2 Deep learning families
- **LSTM / 1D-CNN / ConvLSTM** over KPI time-series and logs — the workhorses for temporal
  anomaly detection ([multi-tiered LSTM+1D-CNN](https://link.springer.com/article/10.1186/s13638-022-02183-7);
  [boosted-LSTM logs](https://pmc.ncbi.nlm.nih.gov/articles/PMC7256370/);
  [offshore O-RAN LSTM](https://www.sciencedirect.com/science/article/pii/S0952197625022821)).
- **Autoencoders / VAEs** — label-free reconstruction; VAEs also enable **interpretable**
  encodings of KPIs like Call Drop Rate ([interpretable VAE](https://arxiv.org/abs/2306.15938)).
- **GANs** — model the normal distribution and flag deviations, and generate minority samples to
  fight imbalance ([RANGAN for 5G Cloud RAN](https://arxiv.org/pdf/2508.20985)).
- **Transformers / self-supervised** — change detection and long-range temporal dependencies
  without labels ([self-supervised transformer](https://arxiv.org/pdf/2302.02025)).

### 5.3 Unsupervised & self-supervised (label-free)
Given §4.2, most production-oriented work is unsupervised (clustering, reconstruction,
one-class) or self-supervised (no labels, no prior anomaly catalogue), sometimes paired with
**automatic labeling** to bootstrap supervised models
([automatic labeling for supervised AD](https://www.sciencedirect.com/science/article/pii/S1877050918320015)).

### 5.4 Ensembles, boosting, transfer learning
Boosted-ensemble LSTM classifiers, GAN-based sampling with consistency checks for imbalanced
drifting streams ([GAN sampling + drift](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10817223/)),
and **multi-scale ConvLSTM with transfer learning** to move models across cells/deployments with
little target data ([multi-scale ConvLSTM + transfer](https://arxiv.org/pdf/2410.03732)).

### 5.5 Spatio-temporal & root-cause analysis (GNNs + Transformers)
Model the RAN as a **graph** (cells/nodes, links/edges); a **GNN encodes topology** while a
**Transformer captures temporal dependencies**, jointly enabling detection *and* RCA that
respects spatio-temporal correlation
([GNN+Transformer RCA](https://arxiv.org/abs/2406.15638);
[graph-convolutional fault diagnosis](https://pmc.ncbi.nlm.nih.gov/articles/PMC10459609/);
[GNN AD in multivariate TS](https://arxiv.org/abs/2106.06947)). Causal approaches trace an event
chain from first anomaly to SLA breach
([causal intervention sequence RCA](https://arxiv.org/pdf/2511.17505)).

### 5.6 Federated learning + digital twins (privacy & data scarcity)
**Federated learning** trains anomaly/intrusion models across sites *without centralizing data*,
deployed inside the **RIC**; a **network digital twin** provides a safe sandbox to train and
evaluate before touching the live network. Hierarchical FL + replay-based **continual learning**
tackles drift while preserving privacy
([FL AD in Open RAN + digital twin](https://genesys-lab.org/papers/EUCNC_2024_DT.pdf);
[Zero-Trust + FL 6G](https://www.mdpi.com/1999-5903/17/6/233)).

### 5.7 Explainable AI & causal RCA (trust)
XAI adds transparency so operators can exclude false positives and design countermeasures —
blending **root-cause discovery, causal sub-graph analysis, and deviation detection** into a
traceable event chain ([XAI RCA](https://www.researchgate.net/publication/400182766);
[explainable RCA in mobile networks](https://www.cyient.com/blog/explainable-smart-root-cause-analysis-in-mobile-networks)).

### 5.8 LLM agents, RAG, and guardrails (the agentic frontier)
- **RAG grounding** — retrieve 3GPP/O-RAN spec chunks at query time instead of relying on model
  weights; the highest-leverage anti-hallucination move. Benchmarks compare **vector vs graph vs
  hybrid** retrieval for O-RAN ([RAG-for-ORAN benchmark](https://arxiv.org/pdf/2507.03608)).
- **Guardrails & tool specs** — constrain agents to predefined tools with validated arguments,
  **reject hallucinated/out-of-scope tools**, and bound KPI values — the pattern in agent
  benchmarks ([TelAgentBench](https://arxiv.org/html/2604.06209)).
- **Fine-tuning** — RAN-specific models (e.g., ORANSight-family) for parameter reasoning
  ([LLM network-mgmt survey](https://onlinelibrary.wiley.com/doi/full/10.1002/nem.70029)).
- **Intent-based networking & multi-agent / edge-agentic** frameworks for autonomous O-RAN
  optimization ([intent-based RAN w/ LLMs](https://arxiv.org/html/2507.14230v1);
  [edge agentic AI O-RAN](https://arxiv.org/pdf/2507.21696);
  [agentic RAN standardization](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12321753/)).

### 5.9 Deployment: xApps / rApps and MLOps
Anomaly detection lives as **xApps (near-RT RIC)** for fast closed loops and **rApps (non-RT RIC)**
for slower analytics/training, with explicit retraining-vs-latency trade-offs
([xApps survey](https://mentis.info/wp-content/uploads/2025/02/xApps_COMNET_2025.pdf)).

### 5.10 Better evaluation
In response to §4.9, the community is building **industrial-grade benchmarks and semantically
aware metrics** (TimeEval, TimeSeriesBench) and telecom-specific agent benchmarks (TelAgentBench,
MM-Telco, 6G-Bench) to measure real progress rather than metric artifacts
([TimeSeriesBench](https://arxiv.org/html/2402.10802v3); [MM-Telco](https://arxiv.org/pdf/2511.13131)).

---

## 6. Industry / real-world state (July 2026)

- **Deutsche Telekom "RAN Guardian" agent is live** — monitors performance, assists
  troubleshooting/optimization; tasks that took ~an hour now take minutes
  ([Deutsche Telekom](https://www.telekom.com/en/media/media-information/archive/ai-agents-for-mobile-network-1099054)).
- **Vendors are all-in** — NVIDIA (NOC assist), Ericsson, Nokia, Vodafone, AT&T, SoftBank on
  GenAI/LLM ops; **AWS** frames agentic AI as the path to **Level-5 autonomous networks**
  ([AWS agentic RAN](https://aws.amazon.com/blogs/industries/agentic-ai-for-ran-optimization-pathway-to-autonomous-network-level-5/)).
- **Standardization is the next bottleneck** — agent tool specs, RAN-specific LLM fine-tuning,
  validation frameworks, and AI-friendly documentation
  ([agentic RAN standardization](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12321753/)).

---

## 7. Implications for RAN Advisor

Mapping the literature onto this repo's drop-rate agent (LangChain4j `@Tool` agent over a live
5G-NSA drops DB, pgvector RAG, two-layer input guardrails, tool-call logging, eval harness):

**What the literature validates about the current design**

| Repo capability | Literature backing |
|---|---|
| **pgvector RAG** over a call-drop knowledge base | RAG is the top-cited anti-hallucination + domain-grounding move for telecom LLMs ([RAG-for-ORAN](https://arxiv.org/pdf/2507.03608)) |
| **Two-layer guardrails** (rule + LLM) | Agent benchmarks require rejecting hallucinated/out-of-scope tools and bounding inputs ([TelAgentBench](https://arxiv.org/html/2604.06209)) |
| **Eval harness** (question → ground-truth fragment, pass rate) | Direct response to the "flawed benchmarks / illusion of progress" problem ([flawed benchmarks](http://ieeexplore.ieee.org/iel7/69/10036334/09537291.pdf)) |
| **Tool-call logging** (`agent_logs`) | Supports auditability/RCA and post-incident learning that XAI work says operators need ([XAI RCA](https://easychair.org/publications/preprint/XBBlj)) |
| **Drop-rate / worst-cell focus** | Retainability/Call-Drop-Rate is a canonical, well-studied KPI target ([interpretable VAE](https://arxiv.org/abs/2306.15938)) |

**Gaps / high-value next steps suggested by the research**

1. **Add root-cause reasoning, not just detection** — the agent surfaces the worst cell + dominant
   cause; the literature pushes toward explicit **causal/RCA chains** (§5.5, §5.7). A
   spatio-temporal (neighbor-cell) view is the recognized 5G-era upgrade.
2. **Guard against concept drift** — worst-cell rankings and the KB will drift; consider drift
   monitoring on the underlying KPIs and a retraining/refresh cadence (§4.4, §4.8).
3. **Handle class imbalance explicitly** if/when statistical detection is added — anomalies are
   rare; precision-first thresholds prevent alarm fatigue (§4.3, §4.5).
4. **Strengthen eval beyond fragment-matching** — adopt semantically aware / telecom-specific eval
   ideas and avoid point-adjustment-style metric inflation (§4.9, §5.10).
5. **Consider a digital-twin / synthetic-data path** for safe evaluation, and a **federated**
   option if data ever spans operators/sites (§5.6).

---

## 8. Key takeaways

1. **Models are commoditized; data + operations + trust are the moat.** Imbalance, labels, drift,
   false positives, explainability, and real-time deployment decide success — not model choice.
2. **Detection is table stakes; root-cause analysis and explanations are what operators buy.**
3. **Ground the LLM, constrain the tools, and measure honestly.** RAG + guardrails + a real eval
   harness are the antidotes to hallucination and the "illusion of progress."
4. **This repo is aimed at the right targets** — its RAG, guardrails, logging, and eval pieces map
   cleanly onto the field's consensus mitigations; the biggest opportunities are RCA depth,
   drift handling, and stronger evaluation.

---

## 9. References

*Links reflect what was reachable during the July 2026 scan; some publisher full texts were gated
and are cited from abstracts/indexed summaries.*

**Surveys & overviews**
- [A Survey of ML Applied to Self-Organizing Cellular Networks](https://www.researchgate.net/publication/318476403)
- [Anomaly Detection in SON: Conventional vs. Contemporary ML](https://www.researchgate.net/publication/361236168)
- [AI advances in anomaly detection for telecom networks (Springer, 2025)](https://link.springer.com/article/10.1007/s10462-025-11108-x)
- [ML-Based Anomaly Detection in NFV: A Comprehensive Survey](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10256098/)
- [6G White Paper on ML in Wireless Communication Networks](https://arxiv.org/pdf/2004.13875)
- [A Comprehensive Survey on LLM-Based Network Management and Operations (Wiley)](https://onlinelibrary.wiley.com/doi/full/10.1002/nem.70029)

**Classical & deep-learning detection on RAN/cellular KPIs**
- [Boosted Ensemble (LSTM) Anomaly Detection in 5G RAN](https://pmc.ncbi.nlm.nih.gov/articles/PMC7256370/)
- [Machine Learning Applied to Anomaly Detection on 5G O-RAN](https://www.sciencedirect.com/science/article/pii/S1877050923009110)
- [ML-Driven Anomaly Detection for 5G O-RAN Performance Metrics](https://arxiv.org/pdf/2509.03290)
- [Anomaly detection in offshore O-RAN using LSTM (cloud-native platform)](https://www.sciencedirect.com/science/article/pii/S0952197625022821)
- [Network load prediction & anomaly detection via ensemble learning in 5G](https://www.sciencedirect.com/science/article/abs/pii/S0140366422004054)
- [Anomaly detection in multi-tiered cellular networks using LSTM & 1D-CNN](https://link.springer.com/article/10.1186/s13638-022-02183-7)
- [Multi-Scale ConvLSTM with Transfer Learning](https://arxiv.org/pdf/2410.03732)
- [Interpretable Anomaly Detection in Cellular Networks via VAEs (Call Drop Rate)](https://arxiv.org/abs/2306.15938)
- [RANGAN: GAN-empowered Anomaly Detection in 5G Cloud RAN](https://arxiv.org/pdf/2508.20985)
- [Self-Supervised Transformer for Change Detection in RAN](https://arxiv.org/pdf/2302.02025)
- [MonTrees: Automated Detection & Classification of Cellular Anomalies](https://arxiv.org/pdf/2108.13156)
- [Anomaly Detection & Classification via Automatic Labeling](https://www.sciencedirect.com/science/article/pii/S1877050918320015)
- [K-means anomaly detection on Call Detail Records](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10974756/)

**Spatio-temporal & root-cause analysis**
- [Root Cause Analysis of Anomalies in 5G RAN using GNN + Transformer (TRACTOR)](https://arxiv.org/abs/2406.15638)
- [Cellular Network Fault Diagnosis via Graph Convolutional NN](https://pmc.ncbi.nlm.nih.gov/articles/PMC10459609/)
- [GNN-Based Anomaly Detection in Multivariate Time Series](https://arxiv.org/abs/2106.06947)
- [Causal Intervention Sequence Analysis for Fault Tracking in RAN](https://arxiv.org/pdf/2511.17505)

**Federated learning, digital twins, O-RAN deployment**
- [Federated Learning for Anomaly Detection in Open RAN (Digital Twin)](https://genesys-lab.org/papers/EUCNC_2024_DT.pdf)
- [Secure & Trustworthy O-RAN: Zero-Trust + FL for 6G](https://www.mdpi.com/1999-5903/17/6/233)
- [O-RAN xApps: Survey and Research Challenges](https://mentis.info/wp-content/uploads/2025/02/xApps_COMNET_2025.pdf)

**Explainability & trust**
- [Explainable AI for Transparent RCA in Mission-Critical Operations](https://www.researchgate.net/publication/400182766)
- [Explainable AI in Network Anomaly Detection: Transparency & Trust](https://easychair.org/publications/preprint/XBBlj)
- [Explainable Smart Root Cause Analysis in Mobile Networks](https://www.cyient.com/blog/explainable-smart-root-cause-analysis-in-mobile-networks)

**LLM / agentic approaches & RAG**
- [Toward standardization of GenAI-driven agentic architectures for RAN](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC12321753/)
- [Intent-Based Network for RAN Management with LLMs](https://arxiv.org/html/2507.14230v1)
- [Edge Agentic AI Framework for Autonomous Network Optimisation in O-RAN](https://arxiv.org/pdf/2507.21696)
- [Foundation Models for Wireless Communications](https://arxiv.org/pdf/2606.06239)
- [Benchmarking Vector, Graph & Hybrid RAG for O-RAN](https://arxiv.org/pdf/2507.03608)
- [NextG-GPT: GenAI for Wireless Networks](https://arxiv.org/pdf/2505.19322)

**Benchmarks & evaluation**
- [Current Time-Series Anomaly-Detection Benchmarks are Flawed (Wu & Keogh)](http://ieeexplore.ieee.org/iel7/69/10036334/09537291.pdf)
- [Open Challenges in Time-Series Anomaly Detection: An Industry Perspective](https://arxiv.org/html/2502.05392v1)
- [Anomaly Detection in Time Series: A Comprehensive Evaluation (TimeEval)](https://timeeval.github.io/evaluation-paper/)
- [TimeSeriesBench: Industrial-Grade TSAD Benchmark](https://arxiv.org/html/2402.10802v3)
- [TelAgentBench: Benchmark for Telecom AI Agents](https://arxiv.org/html/2604.06209)
- [MM-Telco: Benchmarks & Multimodal LLMs for Telecom](https://arxiv.org/pdf/2511.13131)

**Concept drift & data issues**
- [Concept Drift Detection from Multi-Class Imbalanced Data Streams](https://arxiv.org/pdf/2104.10228)
- [Adaptive Anomaly Detection in the Presence of Concept Drift](https://arxiv.org/html/2506.15831)
- [Are Concept Drift Detectors Reliable Alarming Systems?](https://arxiv.org/pdf/2211.13098)
- [Ensemble w/ GAN sampling + consistency for imbalanced drifting streams](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10817223/)
- [Data Anomaly Detection at Scale: Best Practices](https://www.eyer.ai/blog/data-anomaly-detection-at-scale-best-practices/)

**Industry / real-world**
- [Deutsche Telekom: AI agents for mobile network (RAN Guardian)](https://www.telekom.com/en/media/media-information/archive/ai-agents-for-mobile-network-1099054)
- [AWS: Agentic AI for RAN optimization → Level-5 autonomy](https://aws.amazon.com/blogs/industries/agentic-ai-for-ran-optimization-pathway-to-autonomous-network-level-5/)
- [NVIDIA: Generative AI for Network Operations Centers](https://www.nvidia.com/en-us/use-cases/network-operations-assist/)
