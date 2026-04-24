package com.example.libraryseat.attendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class QrCodeService {
    
    private static final int QR_CODE_SIZE = 300;
    private static final String IMAGE_FORMAT = "PNG";
    
    /**
     * 生成座位固定二维码
     * 二维码内容格式：seat:{seatId}（固定格式，不包含时间戳）
     * 每个座位的二维码是固定的，可以打印后贴在座位上
     * @param seatId 座位ID
     * @return Base64编码的二维码图片
     */
    public String generateSeatQrCode(Long seatId) {
        try {
            // 生成固定二维码内容：seat:{seatId}（不包含时间戳，确保二维码固定）
            String content = String.format("seat:%d", seatId);
            
            // 生成二维码图片
            BufferedImage qrImage = createQRImage(content, QR_CODE_SIZE);
            
            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, IMAGE_FORMAT, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            log.error("生成座位二维码失败，座位ID: {}", seatId, e);
            throw new RuntimeException("生成二维码失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据任意内容生成二维码
     * @param content 二维码内容（可以是URL、文本等）
     * @return Base64编码的二维码图片
     */
    public String generateQrCodeFromContent(String content) {
        try {
            // 生成二维码图片
            BufferedImage qrImage = createQRImage(content, QR_CODE_SIZE);
            
            // 转换为Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, IMAGE_FORMAT, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            log.error("生成二维码失败，内容: {}", content, e);
            throw new RuntimeException("生成二维码失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析二维码内容
     * @param qrContent 二维码内容
     * @return 解析结果：{type: "seat"|"checkin", seatId: xxx, reservationId: xxx}
     */
    public Map<String, Object> parseQrCode(String qrContent) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String[] parts = qrContent.split(":");
            if (parts.length < 2) {
                throw new IllegalArgumentException("二维码格式错误");
            }
            
            String type = parts[0];
            result.put("type", type);
            
            if ("seat".equals(type) && parts.length >= 2) {
                // seat:{seatId}（固定格式，不包含时间戳）
                result.put("seatId", Long.parseLong(parts[1]));
            } else if ("checkin".equals(type) && parts.length >= 3) {
                // checkin:{reservationId}:{seatId}:{timestamp}（兼容旧格式）
                result.put("reservationId", Long.parseLong(parts[1]));
                result.put("seatId", Long.parseLong(parts[2]));
            } else {
                throw new IllegalArgumentException("不支持的二维码类型: " + type);
            }
            
            return result;
        } catch (Exception e) {
            log.error("解析二维码失败: {}", qrContent, e);
            throw new IllegalArgumentException("二维码格式错误: " + e.getMessage());
        }
    }
    
    /**
     * 创建二维码图片(外观)
     */
    private BufferedImage createQRImage(String content, int size) throws WriterException {
        // 配置参数（高容错率、UTF-8编码、边距1）
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1);
        
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        // 调用 ZXing 生成黑白点阵矩阵
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        // 创建空白图片
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        //遍历矩阵，黑点设黑色像素，白点设白色像素
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        
        return image;
    }
}
