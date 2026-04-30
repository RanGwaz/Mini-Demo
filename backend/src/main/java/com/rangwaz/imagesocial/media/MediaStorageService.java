package com.rangwaz.imagesocial.media;

import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.config.MinioProperties;
import com.rangwaz.imagesocial.media.dto.UploadResponse;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {

    private static final int THUMB_MAX_EDGE = 720;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MediaStorageService(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
        this.minioClient = MinioClient.builder()
                .endpoint(minioProperties.endpoint())
                .credentials(minioProperties.accessKey(), minioProperties.secretKey())
                .build();
    }

    public UploadResponse uploadImage(MultipartFile file) {
        validateImage(file);
        try {
            ensureBucket();
            byte[] rawBytes = file.getBytes();
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (bufferedImage == null) {
                throw new BusinessException("图片解析失败");
            }

            String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
            String objectKey = "images/" + UUID.randomUUID() + extension;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(rawBytes), rawBytes.length, -1)
                    .contentType(resolveContentType(file.getContentType(), extension))
                    .build());

            ThumbnailResult thumb = buildThumbnail(bufferedImage);
            String thumbObjectKey = "thumbs/" + objectKey.replaceFirst("^images/", "");
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(thumbObjectKey)
                    .stream(new ByteArrayInputStream(thumb.bytes()), thumb.bytes().length, -1)
                    .contentType("image/jpeg")
                    .build());

            String publicBase = minioProperties.publicEndpoint() + "/" + minioProperties.bucket() + "/";
            return new UploadResponse(
                    objectKey,
                    publicBase + objectKey,
                    resolveContentType(file.getContentType(), extension),
                    publicBase + thumbObjectKey,
                    bufferedImage.getWidth(),
                    bufferedImage.getHeight()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("图片上传失败: " + exception.getMessage());
        }
    }

    private ThumbnailResult buildThumbnail(BufferedImage source) throws Exception {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int longEdge = Math.max(sourceWidth, sourceHeight);
        if (longEdge <= 0) {
            throw new BusinessException("图片尺寸非法");
        }

        double scale = longEdge > THUMB_MAX_EDGE ? THUMB_MAX_EDGE / (double) longEdge : 1.0d;
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        thumbnail.getGraphics().drawImage(source, 0, 0, targetWidth, targetHeight, null);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new BusinessException("当前环境不支持 JPEG 编码");
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.82f);
            }
            writer.write(null, new IIOImage(thumbnail, null, null), params);
        } finally {
            writer.dispose();
        }
        return new ThumbnailResult(outputStream.toByteArray(), targetWidth, targetHeight);
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("仅支持图片类型文件");
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.bucket()).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.bucket()).build());
        }
    }

    private String resolveExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if (contentType == null) {
            return ".jpg";
        }
        if (contentType.contains("png")) {
            return ".png";
        }
        if (contentType.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        return switch (extension) {
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private record ThumbnailResult(byte[] bytes, int width, int height) {
    }
}
