package com.facecheck.face.repo;

import com.facecheck.face.model.FacePhoto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FacePhotoRepository extends JpaRepository<FacePhoto, UUID> {

    long countByUserIdAndEnabledTrue(UUID userId);

    List<FacePhoto> findAllByUserIdAndEnabledTrueOrderByCreatedAtDesc(UUID userId);

    Optional<FacePhoto> findByIdAndUserIdAndEnabledTrue(UUID id, UUID userId);
}
