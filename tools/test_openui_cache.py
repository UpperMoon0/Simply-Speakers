"""Cold creation, warm restore and poisoned/incomplete dependency-cache regressions."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import openui_cache as cache


class OpenUiCacheTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name) / "maven"
        self.directory.mkdir()
        self.checkout = Path(self.temp.name) / "source"
        self.checkout.mkdir()
        self.identity = {"distribution": "temurin", "java_major": 21, "java_version": "21.0.8",
                         "java_runtime_version": "21.0.8+9-LTS", "vendor": "Eclipse Adoptium", "jvm_arch": "x86_64"}
        patcher = patch.object(cache, "runtime_toolchain", return_value=self.identity)
        self.original_toolchain = cache.runtime_toolchain
        patcher.start()
        self.addCleanup(patcher.stop)
        self.version = cache.contract()["version"]
        (self.checkout / "gradle.properties").write_text("mod_version = " + self.version)
        for target in cache.TARGETS:
            artifact = "openui-mc-" + target
            directory = self.directory / artifact / self.version
            directory.mkdir(parents=True)
            (directory / f"{artifact}-{self.version}.pom").write_text(
                f'<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>com.nstut</groupId>'
                f'<artifactId>{artifact}</artifactId><version>{self.version}</version></project>')
            with zipfile.ZipFile(directory / f"{artifact}-{self.version}.jar", "w") as jar:
                if target.startswith("fabric-"):
                    jar.writestr("fabric.mod.json", json.dumps({"id": "openui_mc", "version": self.version}))
                else:
                    name = "META-INF/mods.toml" if target.startswith("forge-") else "META-INF/neoforge.mods.toml"
                    jar.writestr(name, f'[[mods]]\nmodId = "openui_mc"\nversion = "{self.version}"\n')
        self.git = patch.object(cache.subprocess, "check_output", side_effect=[cache.SOURCE_PIN + "\n", ""])

    def create(self):
        with self.git:
            cache.create(self.directory, self.checkout)

    def manifest(self):
        return json.loads((self.directory / cache.MANIFEST).read_text())

    def save(self, manifest):
        (self.directory / cache.MANIFEST).write_text(json.dumps(manifest))

    def test_cold_build_then_repeated_warm_validation(self):
        self.create()
        manifest = self.manifest()
        self.assertEqual(len(manifest["files"]), 10)
        self.assertEqual(manifest["source_commit"], cache.SOURCE_PIN)
        cache.validate(self.directory)
        cache.validate(self.directory)
        self.assertEqual(self.manifest(), manifest)

    def test_missing_manifest_rejected(self):
        with self.assertRaises(FileNotFoundError):
            cache.validate(self.directory)

    def test_every_missing_target_jar_or_pom_rejected(self):
        self.create()
        for name in self.manifest()["files"]:
            with self.subTest(name=name):
                path = self.directory / name
                contents = path.read_bytes()
                path.unlink()
                with self.assertRaisesRegex(ValueError, "inventory"):
                    cache.validate(self.directory)
                path.write_bytes(contents)

    def test_corrupted_jar_and_extra_file_rejected(self):
        self.create()
        jar = next(self.directory.rglob("*.jar"))
        jar.write_bytes(b"poison")
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            cache.validate(self.directory)
        (self.directory / "unexpected.jar").write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "inventory"):
            cache.validate(self.directory)

    def test_wrong_provenance_fields_rejected(self):
        self.create()
        original = self.manifest()
        for field in cache.contract():
            with self.subTest(field=field):
                self.save({**original, field: "wrong"})
                with self.assertRaisesRegex(ValueError, "provenance mismatch"):
                    cache.validate(self.directory)
        self.save(original)
        cache.validate(self.directory)

    def test_wrong_pom_version_rejected_even_with_matching_digest(self):
        self.create()
        pom = next(self.directory.rglob("*.pom"))
        pom.write_text(pom.read_text().replace(f"<version>{self.version}</version>", "<version>0.0.0</version>"))
        manifest = self.manifest()
        manifest["files"] = cache.inventory(self.directory)
        self.save(manifest)
        with self.assertRaisesRegex(ValueError, "Wrong OpenUI version"):
            cache.validate(self.directory)

    def test_wrong_jar_version_rejected_even_with_matching_digest(self):
        self.create()
        jar = next(self.directory.rglob("openui-mc-fabric-*.jar"))
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("fabric.mod.json", json.dumps({"id": "openui_mc", "version": "0.0.0"}))
        manifest = self.manifest()
        manifest["files"] = cache.inventory(self.directory)
        self.save(manifest)
        with self.assertRaisesRegex(ValueError, "JAR identity/version"):
            cache.validate(self.directory)

    def test_invalid_jar_rejected_even_with_matching_digest(self):
        self.create()
        next(self.directory.rglob("*.jar")).write_bytes(b"not a zip")
        manifest = self.manifest()
        manifest["files"] = cache.inventory(self.directory)
        self.save(manifest)
        with self.assertRaises(zipfile.BadZipFile):
            cache.validate(self.directory)

    def test_symlink_manifest_and_file_rejected(self):
        self.create()
        manifest = self.directory / cache.MANIFEST
        original = manifest.read_bytes()
        target = Path(self.temp.name) / "elsewhere.json"
        target.write_bytes(original)
        manifest.unlink()
        manifest.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "symlink"):
            cache.validate(self.directory)
        manifest.unlink()
        manifest.write_bytes(original)
        (self.directory / "escape").symlink_to(self.checkout, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symlink"):
            cache.validate(self.directory)

    def test_cold_build_requires_pinned_clean_checkout_and_version(self):
        for git_results in (("a" * 40, ""), (cache.SOURCE_PIN, " M src/Changed.java")):
            with patch.object(cache.subprocess, "check_output", side_effect=git_results):
                with self.assertRaisesRegex(ValueError, "pinned|clean"):
                    cache.create(self.directory, self.checkout)
        (self.checkout / "gradle.properties").write_text("mod_version = 0.0.0")
        with self.git:
            with self.assertRaisesRegex(ValueError, "does not match"):
                cache.create(self.directory, self.checkout)
        self.assertFalse((self.directory / cache.MANIFEST).exists())

    def test_observed_runtime_version_changes_cache_key_and_rejects_warm_manifest(self):
        self.create()
        key = cache.cache_key()
        with patch.object(cache, "runtime_toolchain", return_value={**self.identity, "java_runtime_version": "21.0.9+10-LTS"}):
            self.assertNotEqual(cache.cache_key(), key)
            with self.assertRaisesRegex(ValueError, "toolchain"):
                cache.validate(self.directory)

    def test_observed_release_file_rejects_wrong_java_vendor_or_missing_arch(self):
        home = Path(self.temp.name) / "jdk"
        home.mkdir()
        for version, vendor, arch in (("17.0.8", "Eclipse Adoptium", "x86_64"),
                                      ("21.0.8", "Other vendor", "x86_64"),
                                      ("21.0.8", "Eclipse Adoptium", ""),
                                      ("21.0.8", "Eclipse Adoptium", "x86_64")):
            (home / "release").write_text(f'JAVA_VERSION="{version}"\nIMPLEMENTOR="{vendor}"\nOS_ARCH="{arch}"\n')
            with patch.dict(cache.os.environ, {"JAVA_HOME": str(home)}):
                if version.startswith("21.") and vendor == "Eclipse Adoptium" and arch:
                    self.assertEqual(self.original_toolchain()["java_version"], version)
                else:
                    with self.assertRaisesRegex(ValueError, "actual Temurin"):
                        self.original_toolchain()

    def test_key_namespaces_os_arch_java_and_helper_inputs(self):
        with patch.dict(cache.os.environ, {"RUNNER_OS": "Linux", "RUNNER_ARCH": "X64"}):
            key = cache.cache_key()
            self.assertIn("Linux-X64-temurin21-", key)
            self.assertIn(cache.SOURCE_PIN, key)
            original = cache.contract()
            with patch.object(cache, "contract", return_value={**original, "inputs_sha256": "changed"}):
                self.assertNotEqual(cache.cache_key(), key)
        with patch.dict(cache.os.environ, {"RUNNER_OS": "Linux", "RUNNER_ARCH": "ARM64"}):
            self.assertNotEqual(cache.cache_key(), key)


if __name__ == "__main__":
    unittest.main()
