package com.facecheck.face;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.auth.service.JwtService;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.support.RedisRabbitContainerSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FacePhotoFlowIntegrationTest extends RedisRabbitContainerSupport {

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
    private User anotherUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        huaweiFaceRefRepository.deleteAll();
        facePhotoRepository.deleteAll();
        userRepository.deleteAll();
        owner = saveUser("face-owner", UserRole.USER);
        anotherUser = saveUser("face-another", UserRole.USER);
        adminUser = saveUser("face-admin", UserRole.ADMIN);
    }

    @Test
    void shouldAllowUsersToManageOwnPhotosAndExposePendingStatus() throws Exception {
        MockMultipartFile file = pngFile("self-photo.png");

        String response = mockMvc.perform(multipart("/api/me/face-photos")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.userId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_REGISTER"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String photoId = com.jayway.jsonpath.JsonPath.read(response, "$.data.photoId");

        mockMvc.perform(get("/api/me/face-photos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].photoId").value(photoId))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_REGISTER"));

        mockMvc.perform(delete("/api/me/face-photos/{photoId}", photoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(anotherUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowAdminsToInspectAnyUsersPhotos() throws Exception {
        mockMvc.perform(multipart("/api/me/face-photos")
                        .file(pngFile("admin-visible.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/admin/users/{userId}/face-photos", owner.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("PENDING_REGISTER"));
    }

    @Test
    void shouldRejectTheSixthNonDeletedPhoto() throws Exception {
        for (int index = 0; index < 5; index++) {
            mockMvc.perform(multipart("/api/me/face-photos")
                            .file(pngFile("photo-" + index + ".png"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(multipart("/api/me/face-photos")
                        .file(pngFile("photo-6.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FACE_PHOTO_LIMIT_REACHED"));
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
