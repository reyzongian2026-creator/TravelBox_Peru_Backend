package com.tuempresa.storage.warehouses.application.usecase;

import com.tuempresa.storage.shared.infrastructure.storage.LocalFileStorageService;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class WarehouseImageService {

    private static final String FILE_PREFIX = "/api/v1/files/";

    private final LocalFileStorageService localFileStorageService;

    public WarehouseImageService(LocalFileStorageService localFileStorageService) {
        this.localFileStorageService = localFileStorageService;
    }

    public WarehouseImageContent loadImage(Warehouse warehouse) {
        WarehouseImageContent uploaded = loadUploadedImage(warehouse != null ? warehouse.getPhotoPath() : null);
        if (uploaded != null) {
            return uploaded;
        }
        return new WarehouseImageContent(renderGeneratedImage(warehouse), MediaType.IMAGE_PNG);
    }

    private WarehouseImageContent loadUploadedImage(String photoPath) {
        if (photoPath == null || photoPath.isBlank()) {
            return null;
        }
        try {
            String normalizedPath = URI.create(photoPath).getPath();
            if (normalizedPath == null || !normalizedPath.startsWith(FILE_PREFIX)) {
                return null;
            }
            String relative = normalizedPath.substring(FILE_PREFIX.length());
            int slashIndex = relative.indexOf('/');
            if (slashIndex <= 0 || slashIndex >= relative.length() - 1) {
                return null;
            }
            String category = relative.substring(0, slashIndex);
            String filename = relative.substring(slashIndex + 1);
            Path path = localFileStorageService.resolveForRead(category, filename);
            byte[] content = Files.readAllBytes(path);
            String mime = Files.probeContentType(path);
            MediaType mediaType = (mime == null || mime.isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mime);
            return new WarehouseImageContent(content, mediaType);
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] renderGeneratedImage(Warehouse warehouse) {
        try {
            return renderGeneratedImageInternal(warehouse, true);
        } catch (RuntimeException ignored) {
            return renderGeneratedImageInternal(warehouse, false);
        }
    }

    private byte[] renderGeneratedImageInternal(Warehouse warehouse, boolean drawText) {
        final int width = 1200;
        final int height = 720;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (drawText) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }

            String cityName = safeCityName(warehouse);
            String warehouseName = safeWarehouseName(warehouse);
            String zoneName = safeZoneName(warehouse);
            ScenePalette palette = paletteFor(cityName, warehouseName);
            SceneType sceneType = sceneTypeFor(cityName);

            g.setPaint(new GradientPaint(0, 0, palette.skyTop(), width, height, palette.skyBottom()));
            g.fillRect(0, 0, width, height);

            paintSceneBackground(g, width, height, palette, sceneType);
            paintWarehouseFacade(g, width, height, palette, warehouseName, drawText);
            paintForeground(g, width, height, palette, sceneType);
            paintOverlay(g, width, height, palette, warehouseName, cityName, zoneName, drawText);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar la imagen del almacen.", ex);
        }
    }

    private void paintSceneBackground(
            Graphics2D g,
            int width,
            int height,
            ScenePalette palette,
            SceneType sceneType
    ) {
        int horizonY = (int) (height * 0.58);
        if (sceneType == SceneType.COASTAL) {
            g.setColor(palette.water());
            g.fillRect(0, horizonY - 34, width, 110);
        }

        g.setColor(palette.terrain());
        g.fillRect(0, horizonY, width, height - horizonY);

        if (sceneType == SceneType.MOUNTAIN) {
            Path2D left = new Path2D.Double();
            left.moveTo(0, horizonY + 40);
            left.lineTo(240, 250);
            left.lineTo(470, horizonY + 40);
            left.closePath();
            g.setColor(new Color(97, 114, 134));
            g.fill(left);

            Path2D right = new Path2D.Double();
            right.moveTo(320, horizonY + 30);
            right.lineTo(670, 180);
            right.lineTo(1020, horizonY + 30);
            right.closePath();
            g.setColor(new Color(118, 133, 151));
            g.fill(right);
        }

        if (sceneType == SceneType.DESERT) {
            g.setColor(new Color(230, 190, 123, 180));
            g.fillRoundRect(0, horizonY - 8, width, 140, 120, 120);
            g.setColor(new Color(199, 146, 81, 170));
            g.fillRoundRect(180, horizonY + 24, width - 220, 120, 100, 100);
        }

        if (sceneType == SceneType.URBAN) {
            for (int i = 0; i < 8; i++) {
                int buildingWidth = 56 + (i % 3) * 12;
                int buildingHeight = 130 + (i % 4) * 38;
                int x = 60 + (i * 128);
                int y = horizonY - buildingHeight + 40;
                g.setColor(new Color(87 + i * 8, 104 + i * 6, 122 + i * 4, 170));
                g.fillRoundRect(x, y, buildingWidth, buildingHeight, 18, 18);
            }
        }
    }

    private void paintWarehouseFacade(
            Graphics2D g,
            int width,
            int height,
            ScenePalette palette,
            String warehouseName,
            boolean drawText
    ) {
        int cardX = 140;
        int cardY = 180;
        int cardWidth = 920;
        int cardHeight = 360;

        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(14, 24, 38, 45));
        g.fillRoundRect(cardX + 18, cardY + 24, cardWidth, cardHeight, 42, 42);

        g.setColor(palette.facade());
        g.fill(new RoundRectangle2D.Double(cardX, cardY, cardWidth, cardHeight, 42, 42));

        g.setColor(new Color(255, 255, 255, 34));
        g.fillRoundRect(cardX, cardY, cardWidth, 72, 42, 42);

        g.setColor(palette.accent());
        g.fillRoundRect(cardX + 46, cardY + 52, 300, 86, 26, 26);

        if (drawText) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 38));
            g.drawString(trimLabel(warehouseName, 22), cardX + 74, cardY + 106);
        } else {
            g.setColor(new Color(255, 255, 255, 210));
            g.fillRoundRect(cardX + 74, cardY + 82, 236, 14, 8, 8);
            g.fillRoundRect(cardX + 74, cardY + 106, 182, 12, 8, 8);
            g.fillRoundRect(cardX + 266, cardY + 106, 44, 12, 8, 8);
        }

        g.setColor(new Color(230, 238, 246));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int x = cardX + 88 + (col * 232);
                int y = cardY + 158 + (row * 116);
                g.fillRoundRect(x, y, 146, 86, 22, 22);
                g.setColor(new Color(187, 210, 229));
                g.fillRoundRect(x + 14, y + 14, 118, 58, 16, 16);
                g.setColor(new Color(230, 238, 246));
            }
        }

        g.setColor(new Color(51, 70, 89));
        g.fillRoundRect(cardX + 388, cardY + 196, 150, 164, 24, 24);
        g.setColor(new Color(118, 143, 167));
        g.fillRoundRect(cardX + 410, cardY + 220, 106, 110, 18, 18);

        g.setColor(new Color(22, 34, 48, 84));
        g.setStroke(new BasicStroke(6f));
        g.drawRoundRect(cardX, cardY, cardWidth, cardHeight, 42, 42);
    }

    private void paintForeground(
            Graphics2D g,
            int width,
            int height,
            ScenePalette palette,
            SceneType sceneType
    ) {
        int baseY = (int) (height * 0.77);
        g.setColor(new Color(41, 56, 71, 70));
        g.fillRoundRect(128, baseY, width - 256, 34, 30, 30);

        for (int i = 0; i < 3; i++) {
            int x = 240 + (i * 150);
            g.setColor(new Color(42, 57, 73));
            g.fillRoundRect(x, baseY - 54, 74, 54, 14, 14);
            g.setColor(palette.accent());
            g.fillRoundRect(x + 10, baseY - 44, 54, 36, 12, 12);
            g.setColor(new Color(252, 215, 125));
            g.fillOval(x + 8, baseY - 4, 18, 18);
            g.fillOval(x + 48, baseY - 4, 18, 18);
        }

        if (sceneType == SceneType.COASTAL) {
            g.setColor(new Color(255, 255, 255, 160));
            g.fillRoundRect(850, baseY - 16, 180, 10, 10, 10);
        }
    }

    private void paintOverlay(
            Graphics2D g,
            int width,
            int height,
            ScenePalette palette,
            String warehouseName,
            String cityName,
            String zoneName,
            boolean drawText
    ) {
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(8, 14, 24, 158));
        g.fillRoundRect(64, height - 126, width - 128, 78, 28, 28);

        if (drawText) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 32));
            g.drawString(trimLabel(warehouseName, 34), 102, height - 78);

            g.setColor(new Color(221, 232, 240));
            g.setFont(new Font("SansSerif", Font.PLAIN, 22));
            g.drawString(cityName + "  |  " + trimLabel(zoneName, 26), 104, height - 48);

            g.setColor(new Color(palette.accent().getRed(), palette.accent().getGreen(), palette.accent().getBlue(), 186));
            g.fillRoundRect(width - 278, height - 108, 178, 40, 16, 16);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            g.drawString("TravelBox", width - 232, height - 82);
            return;
        }

        g.setColor(new Color(255, 255, 255, 214));
        g.fillRoundRect(102, height - 90, 304, 14, 8, 8);
        g.setColor(new Color(221, 232, 240, 205));
        g.fillRoundRect(102, height - 64, 236, 10, 8, 8);

        g.setColor(new Color(palette.accent().getRed(), palette.accent().getGreen(), palette.accent().getBlue(), 186));
        g.fillRoundRect(width - 278, height - 108, 178, 40, 16, 16);
        g.setColor(new Color(255, 255, 255, 222));
        g.fillRoundRect(width - 254, height - 90, 130, 12, 8, 8);
    }

    private SceneType sceneTypeFor(String cityName) {
        String normalized = cityName == null ? "" : cityName.toLowerCase(Locale.ROOT);
        if (normalized.contains("cusco") || normalized.contains("puno") || normalized.contains("arequipa")) {
            return SceneType.MOUNTAIN;
        }
        if (normalized.contains("ica") || normalized.contains("nazca") || normalized.contains("paracas")) {
            return SceneType.DESERT;
        }
        if (normalized.contains("mancora") || normalized.contains("piura") || normalized.contains("lima")) {
            return SceneType.COASTAL;
        }
        return SceneType.URBAN;
    }

    private ScenePalette paletteFor(String cityName, String warehouseName) {
        int seed = Math.abs((cityName + "|" + warehouseName).hashCode());
        SceneType sceneType = sceneTypeFor(cityName);
        return switch (sceneType) {
            case COASTAL -> new ScenePalette(
                    new Color(91 + (seed % 26), 170, 205),
                    new Color(248, 214 - (seed % 18), 172),
                    new Color(188, 177, 150),
                    new Color(24, 119 + (seed % 50), 159),
                    new Color(244, 247, 250),
                    new Color(82, 103, 124),
                    new Color(48, 133, 177)
            );
            case MOUNTAIN -> new ScenePalette(
                    new Color(103, 145, 189),
                    new Color(227, 207, 186),
                    new Color(159, 139, 115),
                    new Color(178, 94 + (seed % 35), 62),
                    new Color(248, 244, 236),
                    new Color(92, 78, 68),
                    new Color(108, 136, 168)
            );
            case DESERT -> new ScenePalette(
                    new Color(101, 165, 219),
                    new Color(250, 205, 133),
                    new Color(214, 174, 111),
                    new Color(198, 122 + (seed % 30), 54),
                    new Color(249, 243, 231),
                    new Color(96, 77, 57),
                    new Color(112, 165, 199)
            );
            case URBAN -> new ScenePalette(
                    new Color(98, 131, 175),
                    new Color(228, 199, 178),
                    new Color(160, 170, 181),
                    new Color(31, 136 + (seed % 40), 118),
                    new Color(244, 247, 249),
                    new Color(68, 85, 101),
                    new Color(108, 143, 174)
            );
        };
    }

    private String trimLabel(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "TravelBox";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeCityName(Warehouse warehouse) {
        if (warehouse == null) {
            return "Peru";
        }
        try {
            if (warehouse.getCity() != null && warehouse.getCity().getName() != null) {
                String cityName = warehouse.getCity().getName().trim();
                if (!cityName.isEmpty()) {
                    return cityName;
                }
            }
        } catch (Exception ignored) {
        }
        return "Peru";
    }

    private String safeWarehouseName(Warehouse warehouse) {
        if (warehouse == null || warehouse.getName() == null || warehouse.getName().isBlank()) {
            return "TravelBox";
        }
        return warehouse.getName().trim();
    }

    private String safeZoneName(Warehouse warehouse) {
        if (warehouse == null) {
            return "Servicio seguro";
        }
        try {
            if (warehouse.getZone() != null && warehouse.getZone().getName() != null) {
                String zoneName = warehouse.getZone().getName().trim();
                if (!zoneName.isEmpty()) {
                    return zoneName;
                }
            }
        } catch (Exception ignored) {
        }
        return "Servicio seguro";
    }

    public record WarehouseImageContent(byte[] bytes, MediaType mediaType) {
    }

    private record ScenePalette(
            Color skyTop,
            Color skyBottom,
            Color terrain,
            Color accent,
            Color facade,
            Color shadow,
            Color water
    ) {
    }

    private enum SceneType {
        COASTAL,
        MOUNTAIN,
        DESERT,
        URBAN
    }
}
