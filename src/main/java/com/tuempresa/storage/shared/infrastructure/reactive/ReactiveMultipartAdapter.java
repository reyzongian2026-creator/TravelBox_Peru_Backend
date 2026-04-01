package com.tuempresa.storage.shared.infrastructure.reactive;

import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ReactiveMultipartAdapter {

    public Mono<MultipartFile> toMultipartFile(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    MediaType mediaType = filePart.headers().getContentType();
                    String contentType = mediaType == null
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : mediaType.toString();
                    return new InMemoryMultipartFile(
                            filePart.name(),
                            filePart.filename(),
                            contentType,
                            bytes
                    );
                });
    }

    public Mono<List<MultipartFile>> toMultipartFiles(List<FilePart> fileParts) {
        if (fileParts == null || fileParts.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(fileParts)
                .concatMap(this::toMultipartFile)
                .collectList();
    }

    @SuppressWarnings("null")
    private static final class InMemoryMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private InMemoryMultipartFile(
                String name,
                String originalFilename,
                String contentType,
                byte[] bytes
        ) {
            this.name = StringUtils.hasText(name) ? name : "file";
            this.originalFilename = StringUtils.hasText(originalFilename) ? originalFilename : "upload.bin";
            this.contentType = StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            if (dest == null) {
                throw new IllegalArgumentException("Destination file is required.");
            }
            Path target = dest.toPath();
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, bytes);
        }
    }
}
