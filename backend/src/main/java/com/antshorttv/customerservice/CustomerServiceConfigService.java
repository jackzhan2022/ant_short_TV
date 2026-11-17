package com.antshorttv.customerservice;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CustomerServiceConfigService {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectStorageService storage;

    public CustomerServiceConfigService(JdbcTemplate jdbc, ObjectStorageService storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    public Config current() {
        String path = jdbc.queryForObject("select qr_code_path from customer_service_config where id=1", String.class);
        return new Config(path == null ? "" : storage.publicUrl(path));
    }

    @Transactional
    public Config save(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalidImage();
        byte[] bytes;
        String format;
        try {
            try (var source = file.getInputStream()) {
                bytes = source.readNBytes(MAX_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalidImage();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalidImage();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!format.equals("png") && !format.equals("jpeg")) throw invalidImage();
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width <= 0 || height <= 0 || width > 4096 || height > 4096
                        || (long) width * height > 16_000_000 || reader.read(0) == null) throw invalidImage();
                } finally {
                    reader.dispose();
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidImage();
        }
        String path = "platform/customer-service/" + UUID.randomUUID() + (format.equals("png") ? ".png" : ".jpg");
        storage.upload(path, bytes, format.equals("png") ? "image/png" : "image/jpeg");
        jdbc.update("update customer_service_config set qr_code_path=?, updated_at=current_timestamp where id=1", path);
        return current();
    }

    @Transactional
    public Config remove() {
        jdbc.update("update customer_service_config set qr_code_path=null, updated_at=current_timestamp where id=1");
        return new Config("");
    }

    private BusinessException invalidImage() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传不超过 2 MB、边长不超过 4096 像素的有效 PNG 或 JPG 二维码图片。");
    }

    public record Config(String qrCodeUrl) { }
}
