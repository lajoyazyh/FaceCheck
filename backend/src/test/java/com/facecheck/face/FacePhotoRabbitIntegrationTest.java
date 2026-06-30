package com.facecheck.face;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.auth.service.JwtService;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.face.model.FaceDetectStatus;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;
import com.facecheck.face.model.HuaweiFaceRef;
import com.facecheck.face.model.HuaweiFaceRefStatus;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.support.RedisRabbitContainerSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.rabbitmq.listener.simple.auto-startup=true"
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FacePhotoRabbitIntegrationTest extends RedisRabbitContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FacePhotoRepository facePhotoRepository;

    @Autowired
    private HuaweiFaceRefRepository huaweiFaceRefRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        huaweiFaceRefRepository.deleteAll();
        facePhotoRepository.deleteAll();
        userRepository.deleteAll();
        owner = saveUser("rabbit-owner", UserRole.USER);
    }

    @Test
    void shouldRegisterUploadedPhotoThroughRabbitListener() throws Exception {
        String response = mockMvc.perform(multipart("/api/me/face-photos")
                        .file(pngFile("rabbit-photo.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING_REGISTER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String photoId = com.jayway.jsonpath.JsonPath.read(response, "$.data.photoId");
        FacePhoto photo = awaitRegisteredPhoto(UUID.fromString(photoId));

        assertThat(photo.getDetectStatus()).isEqualTo(FaceDetectStatus.PASSED);
        assertThat(photo.getRegisterStatus()).isEqualTo(FaceRegisterStatus.ACTIVE);

        HuaweiFaceRef faceRef = huaweiFaceRefRepository.findByFacePhotoId(photo.getId()).orElseThrow();
        assertThat(faceRef.getStatus()).isEqualTo(HuaweiFaceRefStatus.ACTIVE);
        assertThat(faceRef.getExternalImageId()).isEqualTo(photoId);
        assertThat(faceRef.getExternalFields()).containsEntry("photoId", photoId);
        assertThat(faceRef.getExternalFields()).containsEntry("userId", owner.getId().toString());
    }

    private FacePhoto awaitRegisteredPhoto(UUID photoId) throws InterruptedException {
        FacePhoto photo = facePhotoRepository.findById(photoId).orElseThrow();
        for (int attempt = 0; attempt < 20; attempt++) {
            if (photo.getRegisterStatus() == FaceRegisterStatus.ACTIVE) {
                return photo;
            }
            Thread.sleep(250);
            photo = facePhotoRepository.findById(photoId).orElseThrow();
        }
        return photo;
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issue(user).accessToken();
    }

    private User saveUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash("password123"));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private MockMultipartFile pngFile(String name) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return new MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, outputStream.toByteArray());
    }
}
