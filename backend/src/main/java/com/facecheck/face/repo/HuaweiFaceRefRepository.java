package com.facecheck.face.repo;

import com.facecheck.face.model.HuaweiFaceRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HuaweiFaceRefRepository extends JpaRepository<HuaweiFaceRef, UUID> {

    Optional<HuaweiFaceRef> findByFacePhotoId(UUID facePhotoId);

    List<HuaweiFaceRef> findAllByFacePhotoId(UUID facePhotoId);

    Optional<HuaweiFaceRef> findByFrsFaceId(String frsFaceId);
}
