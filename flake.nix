{
  description = "Minimal Gramophone Android build environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs =
    { nixpkgs, ... }:
    let
      systems = [
        "aarch64-darwin"
        "x86_64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];
      forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f system);
    in
    {
      devShells = forAllSystems (
        system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config.allowUnfree = true;
          };

          # JDK 25 is the newest LTS that Gradle 9.4.1 can run on.
          # JDK 27 exists, but Gradle 9.4.1 only supports up to Java 26.
          jdk = pkgs.temurin-bin-25;

          androidEnv = pkgs.callPackage "${pkgs.path}/pkgs/development/mobile/androidenv" {
            inherit pkgs;
            licenseAccepted = true;
          };

          android = androidEnv.composeAndroidPackages {
            platformVersions = [ "36" "37" ];
            buildToolsVersions = [ "36.0.0" "37.0.0" ];
            cmakeVersions = [ "3.22.1" ];
            includeNDK = true;
            ndkVersion = "28.2.13676358";
            includeEmulator = false;
            includeSystemImages = false;
          };

          sdk = android.androidsdk;
          sdkRoot = "${sdk}/libexec/android-sdk";
        in
        {
          default = pkgs.mkShell {
            name = "gramophone";

            packages = [
              jdk
              sdk
              pkgs.git # needed for submodules; jj cannot manage them
              pkgs.pkg-config
            ];

            JAVA_HOME = "${jdk}";
            ANDROID_HOME = sdkRoot;
            ANDROID_SDK_ROOT = sdkRoot;
            ANDROID_NDK_ROOT = "${sdkRoot}/ndk/28.2.13676358";

            shellHook = ''
              if [ ! -f local.properties ]; then
                printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
              fi

              if [ -z "$(ls -A media3 2>/dev/null)" ]; then
                echo "warning: git submodules are not initialized"
                echo "run: git submodule update --init --recursive"
              fi

              echo "Gramophone dev shell: $(java -version 2>&1 | head -n1)"
            '';
          };
        }
      );
    };
}
