package com.applymate;

import com.applymate.ai.AiException;
import com.applymate.ai.Schemas.SkillExtraction;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AvatarAndResumeVersionTest extends IntegrationTest {

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** A JPEG with a comment segment standing in for EXIF/GPS metadata. */
    private static byte[] jpegWithSecretMetadata() throws Exception {
        byte[] jpeg = image("jpg", 800, 600);
        byte[] secret = "GPS-51.0447N-114.0719W".getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                               // SOI
        out.write(new byte[]{(byte) 0xFF, (byte) 0xFE});     // COM marker
        int len = secret.length + 2;
        out.write(new byte[]{(byte) (len >> 8), (byte) len});
        out.write(secret);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, byte[] bytes, String name) throws Exception {
        return mvc.perform(multipart("/profile/avatar").file(new MockMultipartFile("file", name, "image/jpeg", bytes))
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void avatarIsCroppedResizedAndStrippedOfMetadata() throws Exception {
        String token = newUser();
        byte[] original = jpegWithSecretMetadata();
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("GPS-51.0447N");

        upload(token, original, "me.jpg").andExpect(status().isOk());
        call(get("/profile"), token).andExpect(jsonPath("$.avatarUpdatedAt").isNotEmpty());

        byte[] stored = mvc.perform(get("/profile/avatar").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage avatar = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(avatar.getWidth()).isEqualTo(256);
        assertThat(avatar.getHeight()).isEqualTo(256);
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-51.0447N");

        call(delete("/profile/avatar"), token).andExpect(status().isNoContent());
        call(get("/profile/avatar"), token).andExpect(status().isNotFound());
    }

    @Test
    void decompressionBombIsRefusedBeforeDecoding() throws Exception {
        String token = newUser();
        byte[] gif = image("gif", 1, 1);
        // Claim 65535 x 65535 pixels in the screen and image descriptors; the file stays tiny.
        gif[6] = gif[7] = gif[8] = gif[9] = (byte) 0xFF;
        for (int i = 13; i < gif.length - 9; i++) {
            if (gif[i] == 0x2C && gif[i + 5] == 1 && gif[i + 7] == 1) {
                gif[i + 5] = gif[i + 6] = gif[i + 7] = gif[i + 8] = (byte) 0xFF;
                break;
            }
        }
        upload(token, gif, "bomb.gif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("too large")));
    }

    @Test
    void nonImagesAreRejectedWhateverTheirName() throws Exception {
        String token = newUser();
        upload(token, "<?php echo 'hi'; ?>".getBytes(StandardCharsets.UTF_8), "avatar.png").andExpect(status().isBadRequest());
    }

    @Test
    void anEarlierResumeCanBeMadeActiveAgainKeepingTheMaster() throws Exception {
        String token = newUser();
        when(llm.generate(eq("skill-extraction"), anyString(), anyString(), eq(SkillExtraction.class)))
                .thenThrow(AiException.disabled());
        long first = read(mvc.perform(multipart("/resumes").file(new MockMultipartFile("file", "v1.txt", "text/plain",
                ResumeAndAiTest.RESUME.getBytes(StandardCharsets.UTF_8))).header("Authorization", "Bearer " + token)))
                .get("resume").get("id").asLong();
        send(put("/resumes/active/master"), token, Map.of("fullName", "Jordan", "sections", List.of(), "skills", List.of()));
        mvc.perform(multipart("/resumes").file(new MockMultipartFile("file", "v2.txt", "text/plain",
                (ResumeAndAiTest.RESUME + "\nMore").getBytes(StandardCharsets.UTF_8))).header("Authorization", "Bearer " + token));

        call(post("/resumes/" + first + "/activate"), token).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        JsonNode list = read(call(get("/resumes"), token));
        assertThat(list).hasSize(2);
        assertThat(list.findValues("active").stream().filter(JsonNode::asBoolean).count()).isEqualTo(1);
        call(get("/profile"), token).andExpect(jsonPath("$.resume.fileName").value("v1.txt"));
        call(get("/resumes/active/master"), token).andExpect(jsonPath("$.content.fullName").value("Jordan"));

        String stranger = newUser();
        call(post("/resumes/" + first + "/activate"), stranger).andExpect(status().isNotFound());
    }
}
