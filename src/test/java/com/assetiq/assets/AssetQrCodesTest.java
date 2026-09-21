package com.assetiq.assets;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssetQrCodes")
class AssetQrCodesTest {

    private static final UUID ID = UUID.fromString("4f1c2d3e-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    @Test
    @DisplayName("a label encodes a link to the scan page")
    void linkPointsAtTheScanPage() {
        assertThat(AssetQrCodes.link("https://app.example.com/", ID))
                .isEqualTo("https://app.example.com/scan?a=" + ID);
    }

    @ParameterizedTest(name = "reads {0}")
    @ValueSource(strings = {
            "https://app.example.com/scan?a=4f1c2d3e-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "https://app.example.com/scan?x=1&a=4f1c2d3e-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "asset:4f1c2d3e-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "ASSET:4f1c2d3e-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
            "  4f1c2d3e-5a6b-4c7d-8e9f-0a1b2c3d4e5f  "
    })
    void readsEveryFormatEverPrinted(String scanned) {
        assertThat(AssetQrCodes.parse(scanned)).contains(ID);
    }

    @ParameterizedTest(name = "rejects {0}")
    @ValueSource(strings = {"", "hello", "asset:not-a-uuid", "https://app.example.com/scan", "https://x/scan?a=zzz"})
    void rejectsJunk(String scanned) {
        assertThat(AssetQrCodes.parse(scanned)).isEmpty();
    }

    @Test
    @DisplayName("the generated QR image decodes back to the scan link")
    void qrImageRoundTrips() throws Exception {
        String link = AssetQrCodes.link("https://app.example.com", ID);
        var matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 300, 300,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
        var image = MatrixToImageWriter.toBufferedImage(matrix);

        String decoded = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();

        assertThat(decoded).isEqualTo(link);
        assertThat(AssetQrCodes.parse(decoded)).contains(ID);
    }
}
