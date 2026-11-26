package dev.nightclient.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.cosmetics.OfficialCapes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CosmeticsImagesTest {
  @Test
  void upgradesLegacy64x32SkinAndDetectsSlimAndClassic() throws Exception {
    BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    legacy.setRGB(8, 8, 0xffd18862);
    legacy.setRGB(40, 20, 0xff2b59c3);
    byte[] png = toPng(legacy);

    CosmeticsImages.DecodedTexture upgraded = CosmeticsImages.decodeSkin(png);
    assertEquals(64, upgraded.width);
    assertEquals(64, upgraded.height);
    assertEquals(0xff2b59c3, upgraded.pixels[52 * 64 + 47]);

    BufferedImage fourK = new BufferedImage(4096, 4096, BufferedImage.TYPE_INT_ARGB);
    fourK.setRGB(0, 0, 0xff112233);
    byte[] fourKPng = toPng(fourK);
    CosmeticsImages.DecodedTexture decoded4k = CosmeticsImages.decodeSkin(fourKPng);
    assertEquals(4096, decoded4k.width);
    assertEquals(4096, decoded4k.height);
    assertEquals(0xff112233, decoded4k.pixels[0]);
    assertTrue(decoded4k.slim);

    BufferedImage classic = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    classic.setRGB(54, 24, 0xff334455);
    CosmeticsImages.DecodedTexture decodedClassic = CosmeticsImages.decodeSkin(toPng(classic));
    assertFalse(decodedClassic.slim);
  }

  @Test
  void preservesValid2To1CapesUpTo4kAndMapsArtworkToMojangCapeSheet() throws Exception {
    BufferedImage sheet = new BufferedImage(4096, 2048, BufferedImage.TYPE_INT_ARGB);
    sheet.setRGB(128, 128, 0xffaa2222);
    CosmeticsImages.DecodedTexture decodedSheet = CosmeticsImages.decodeCape(toPng(sheet));
    assertEquals(4096, decodedSheet.width);
    assertEquals(2048, decodedSheet.height);
    assertEquals(0xffaa2222, decodedSheet.pixels[128 * 4096 + 128]);

    BufferedImage art = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 512; y++)
      for (int x = 0; x < 512; x++) art.setRGB(x, y, 0xff22aa55);
    CosmeticsImages.DecodedTexture wrapped = CosmeticsImages.decodeCape(toPng(art));
    assertEquals(wrapped.width, wrapped.height * 2);
    assertTrue(wrapped.width >= 2048 && wrapped.width <= 4096);
    int k = wrapped.width / 64;
    assertEquals(0xff22aa55, wrapped.pixels[(8 * k) * wrapped.width + (5 * k)]);

    BufferedImage valentine = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 64; x++) valentine.setRGB(x, y, 0xffffffff);
    for (int y = 1; y < 17; y++)
      for (int x = 1; x < 11; x++) valentine.setRGB(x, y, 0xffb61623);
    byte[] valBytes = toPng(valentine);
    CosmeticsImages.DecodedTexture officialVal = CosmeticsImages.decodeOfficialCape(valBytes);
    CosmeticsImages.DecodedTexture customVal = CosmeticsImages.decodeCape(valBytes);
    assertEquals(64, officialVal.width);
    assertEquals(0xffb61623, officialVal.pixels[5 * 64 + 5]);
    assertEquals(0xffb61623, customVal.pixels[5 * 64 + 5]);
  }

  @Test
  void synthesizesCrispFallbackTextureForAllOfficialCapes() {
    for (OfficialCapes.Cape cape : OfficialCapes.all()) {
      CosmeticsImages.DecodedTexture tex = CosmeticsImages.synthesizeCape(cape);
      assertEquals(64, tex.width);
      assertEquals(32, tex.height);
      assertTrue((tex.pixels[8 * 64 + 5] >>> 24) > 200);
    }
  }

  private static byte[] toPng(BufferedImage img) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "png", out);
    return out.toByteArray();
  }
}
