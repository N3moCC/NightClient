package dev.nightclient.renderer.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.nightclient.cosmetics.CosmeticModels;
import dev.nightclient.renderer.player.DefaultSkins;
import dev.nightclient.renderer.player.PlayerPreviewRenderer;
import dev.nightclient.renderer.player.PlayerSkinData;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CosmeticModelLoaderTest {

  @Test
  void buildsAll2dMinecraftSwordShapesAndMaterialsAndValidatesBuffers() {
    assertEquals(10, CosmeticModels.weapons().size());
    assertEquals(10, CosmeticModels.materials().size());
    assertNotNull(CosmeticModels.materialById("diamond"));
    assertNotNull(CosmeticModels.materialById("netherite"));
    assertNotNull(CosmeticModels.materialById("emerald"));
    for (CosmeticModels.Entry entry : CosmeticModels.weapons()) {
      for (CosmeticModels.Material mat : CosmeticModels.materials()) {
        String label = entry.id + ":" + mat.id;
        CosmeticModelMesh mesh = PixelSwordMeshBuilder.build(entry.id, mat.id);
        assertNotNull(mesh, "Failed building 2D pixel sword " + label);
        assertTrue(mesh.hasBone(CosmeticModelMesh.BONE_WEAPON), label + " missing BONE_WEAPON");
        assertEquals(mesh.vertexCount * CosmeticModelMesh.VERTEX_STRIDE, mesh.vertexBytes.length);
        assertEquals(0, mesh.indices.length % 3);
        assertTrue(mesh.triangleCount >= 200, label + " expected >=200 tris, got " + mesh.triangleCount);
        assertValidIndicesAndBounds(label, mesh);
        assertNotNull(mesh.preview);
        assertEquals(mesh.triangleCount, mesh.preview.triangleCount);
        float thickness = mesh.maxX - mesh.minX;
        assertTrue(thickness >= 0.025f && thickness <= 0.065f,
            label + " unexpected 2D thickness: " + thickness);
        float height = mesh.maxY - mesh.minY;
        assertTrue(height >= 0.65f && height <= 1.35f, label + " unexpected sword height: " + height);
      }
    }
  }

  @Test
  void builtIn3dArmorCatalogIsDisabled() {
    assertEquals(0, CosmeticModels.armors().size());
  }

  @Test
  void loadsCustomObjWithMtlBoneGroupsAndAutoNormalization(@TempDir Path temp) throws Exception {
    String mtl = ""
        + "newmtl steel\n"
        + "Kd 0.8 0.85 0.92\n"
        + "Pm 0.9\n"
        + "Pr 0.2\n"
        + "newmtl plasma\n"
        + "Kd 0.2 0.8 1.0\n"
        + "Ke 0.3 0.9 1.0\n"
        + "d 0.7\n";
    Files.write(temp.resolve("custom.mtl"), mtl.getBytes(StandardCharsets.UTF_8));

    String weaponObj = ""
        + "mtllib custom.mtl\n"
        + "o blade\n"
        + "usemtl plasma\n"
        + "v -5 0 0\n"
        + "v 5 0 0\n"
        + "v 5 120 0\n"
        + "v -5 120 0\n"
        + "v 0 60 8\n"
        + "f 1 2 3 4\n"
        + "f 1 2 5\n"
        + "f 2 3 5\n"
        + "f 3 4 5\n"
        + "f 4 1 5\n";
    Path weaponPath = temp.resolve("giant_sword.obj");
    Files.write(weaponPath, weaponObj.getBytes(StandardCharsets.UTF_8));

    CosmeticModelMesh weapon = CosmeticModelLoader.loadFile(weaponPath, false);
    assertNotNull(weapon);
    assertTrue(weapon.hasBone(CosmeticModelMesh.BONE_WEAPON));
    assertTrue(weapon.triangleCount >= 6);
    float height = weapon.maxY - weapon.minY;
    assertTrue(height > 0.8f && height < 2.2f, "Expected normalized weapon height, got " + height);

    String armorObj = ""
        + "mtllib custom.mtl\n"
        + "usemtl steel\n"
        + "o helmet\n"
        + "v -0.2 0.0 -0.2\nv 0.2 0.0 -0.2\nv 0.2 0.4 0.2\nv -0.2 0.4 0.2\n"
        + "f 1 2 3 4\n"
        + "o chestplate\n"
        + "v -0.25 -0.7 0.0\nv 0.25 -0.7 0.0\nv 0.25 0.0 0.1\nv -0.25 0.0 0.1\n"
        + "f 5 6 7 8\n"
        + "o left_boot\n"
        + "v -0.1 -0.75 -0.1\nv 0.1 -0.75 -0.1\nv 0.1 -0.5 0.1\nv -0.1 -0.5 0.1\n"
        + "f 9 10 11 12\n";
    Path armorPath = temp.resolve("custom_armor.obj");
    Files.write(armorPath, armorObj.getBytes(StandardCharsets.UTF_8));

    CosmeticModelMesh armor = CosmeticModelLoader.loadFile(armorPath, true);
    assertNotNull(armor);
    assertTrue(armor.hasBone(CosmeticModelMesh.BONE_HEAD), "Expected helmet mapped to BONE_HEAD");
    assertTrue(armor.hasBone(CosmeticModelMesh.BONE_BODY), "Expected chestplate mapped to BONE_BODY");
    assertTrue(armor.hasBone(CosmeticModelMesh.BONE_LEFT_BOOT), "Expected left_boot mapped to BONE_LEFT_BOOT");
    assertFalse(armor.hasBone(CosmeticModelMesh.BONE_RIGHT_ARM));
  }

  @Test
  void previewRendererDraws3dWeaponAndPresetCard() throws Exception {
    CosmeticModelMesh sword = PixelSwordMeshBuilder.build("classic", "diamond");
    CosmeticModelMesh netheriteKatana = PixelSwordMeshBuilder.build("katana", "netherite");

    int w = 200, h = 260;
    int[] bareOut = new int[w * h];
    int[] dressedOut = new int[w * h];
    PlayerPreviewRenderer renderer = new PlayerPreviewRenderer();
    PlayerSkinData bare = DefaultSkins.classic();
    PlayerSkinData dressed =
        bare.withCosmeticModels(sword, 0xFF33EBCB, 1.0f, null, 0, 1.0f, 0);

    renderer.render(bare, -22f, 6f, 1f, 0f, 0f, 0f, w, h, bareOut);
    renderer.render(dressed, -22f, 6f, 1f, 0f, 0f, 0f, w, h, dressedOut);

    int changed = 0;
    for (int i = 0; i < bareOut.length; i++) {
      if (bareOut[i] != dressedOut[i]) changed++;
    }
    assertTrue(changed > 350, "2D extruded sword must visibly render on player preview, changed=" + changed);

    int cw = 128, ch = 128;
    int[] diamondCardPx = new int[cw * ch];
    int[] netheriteCardPx = new int[cw * ch];
    PlayerSkinData weaponCard = PlayerSkinData.forModelCard(sword, 0xFF33EBCB, false);
    PlayerSkinData katanaCard = PlayerSkinData.forModelCard(netheriteKatana, 0xFF52464E, false);
    assertTrue(PlayerPreviewRenderer.hasModelCard(weaponCard));

    PlayerPreviewRenderer.renderCosmeticCard(weaponCard, cw, ch, diamondCardPx);
    PlayerPreviewRenderer.renderCosmeticCard(katanaCard, cw, ch, netheriteCardPx);

    int weaponOpaque = countOpaque(diamondCardPx);
    assertTrue(weaponOpaque > 250, "Weapon 2D card should render visible silhouette, got " + weaponOpaque);
    int diffs = 0;
    for (int i = 0; i < diamondCardPx.length; i++) {
      if (diamondCardPx[i] != netheriteCardPx[i]) diffs++;
    }
    assertTrue(diffs > 200, "Distinct form/material cards must produce visibly distinct renders");
  }

  private static int countOpaque(int[] pixels) {
    int count = 0;
    for (int p : pixels) {
      if ((p >>> 24) > 32) count++;
    }
    return count;
  }

  private static void assertValidIndicesAndBounds(String id, CosmeticModelMesh mesh) {
    assertTrue(Float.isFinite(mesh.minX) && Float.isFinite(mesh.maxX), id + " non-finite X");
    assertTrue(Float.isFinite(mesh.minY) && Float.isFinite(mesh.maxY), id + " non-finite Y");
    assertTrue(Float.isFinite(mesh.minZ) && Float.isFinite(mesh.maxZ), id + " non-finite Z");
    assertTrue(mesh.maxX >= mesh.minX && mesh.maxY >= mesh.minY && mesh.maxZ >= mesh.minZ);
    for (int idx : mesh.indices) {
      assertTrue(idx >= 0 && idx < mesh.vertexCount, id + " invalid vertex index " + idx);
    }
    int totalSubIndices = 0;
    for (CosmeticModelMesh.SubMesh sub : mesh.subMeshes) {
      assertTrue(sub.firstIndex >= 0 && sub.firstIndex + sub.indexCount <= mesh.indices.length);
      assertEquals(0, sub.indexCount % 3);
      totalSubIndices += sub.indexCount;
    }
    assertEquals(mesh.indices.length, totalSubIndices, id + " submeshes must cover all indices");
  }
}
