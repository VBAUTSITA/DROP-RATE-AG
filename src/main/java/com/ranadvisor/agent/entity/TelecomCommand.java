package com.ranadvisor.agent.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "mml_commands")
public class TelecomCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_command")
    private Long idCommand;

    private String vendor;

    @Column(name = "command_code")
    private String commandCode;

    private String category;

    // VARCHAR2(4000) rather than CLOB: TelecomCommandRepository.findByKeyword does
    // LOWER(...) LIKE on this column, and Hibernate rejects that against a CLOB path.
    @Column(length = 4000)
    private String description;

    // Getters
    public Long getIdCommand()    { return idCommand; }
    public String getVendor()     { return vendor; }
    public String getCommandCode(){ return commandCode; }
    public String getCategory()   { return category; }
    public String getDescription(){ return description; }
}
