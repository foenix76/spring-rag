package com.example.korrag.repository;

import com.example.korrag.entity.ApplicantEssay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicantEssayRepository extends JpaRepository<ApplicantEssay, String> {
}
