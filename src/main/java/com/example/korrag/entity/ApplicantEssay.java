package com.example.korrag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "REC_APPLI_MAS", schema = "test")
@Getter
@NoArgsConstructor
public class ApplicantEssay {

    @Id
    @Column(name = "acceptno")
    private String acceptNo;

    @Column(name = "name")
    private String name;

    @Column(name = "hsgessay1")
    private String hsgEssay1;

    @Column(name = "hsgessay2")
    private String hsgEssay2;

    @Column(name = "hsgessay3")
    private String hsgEssay3;

    @Column(name = "hsgessay4")
    private String hsgEssay4;
}
