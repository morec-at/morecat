{
  description = "morecat dev environment (Scala/sbt + Rust + Node)";

  inputs = {
    # JDK 25 を使うため unstable を採用。
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfreePredicate = pkg: (nixpkgs.lib.getName pkg) == "terraform";
        };
        # Scala 3.8.x のコンパイラブリッジは JDK 17+ を要求するため 25 を固定。
        jdk = pkgs.jdk25;
        sbt = pkgs.sbt.override { jre = jdk; };

        scalaPkgs = [ jdk sbt pkgs.coursier pkgs.firebase-tools ]; # apps/api
        rustPkgs = [
          pkgs.cargo
          pkgs.rustc
          pkgs.rustfmt
          pkgs.clippy
          pkgs.cargo-llvm-cov
          pkgs.llvmPackages_21.llvm.out
          pkgs.pkg-config
          pkgs.openssl
          pkgs.firebase-tools
          jdk
        ]; # apps/rmu
        nodePkgs = [ pkgs.nodejs_22 pkgs.pnpm ]; # apps/ui

        # nixpkgs の他のツールを更新せず、Terraform の公式配布物を固定する。
        terraformVersion = "1.17.1";
        terraformPlatforms = {
          x86_64-linux = { platform = "linux_amd64"; sha256 = "4638628058e71a55212b1c563712eac03b902128f40740f3dc8174df12135061"; };
          aarch64-linux = { platform = "linux_arm64"; sha256 = "988e950a1c23058093b0e66ab2d5fb53fc18345ecfbd5871acf51dbac9325edd"; };
          x86_64-darwin = { platform = "darwin_amd64"; sha256 = "fde92387cf9c0de74255003c85291f8fbede54585c10d0635ca7a43633d4d171"; };
          aarch64-darwin = { platform = "darwin_arm64"; sha256 = "b1ecb3fe0d0a0290c1ab6f83d967d43f25da05d0b671ee0fb355252268dce95a"; };
        };
        terraformPlatform = terraformPlatforms.${system};
        terraform = pkgs.stdenvNoCC.mkDerivation {
          pname = "terraform";
          version = terraformVersion;
          src = pkgs.fetchurl {
            url = "https://releases.hashicorp.com/terraform/${terraformVersion}/terraform_${terraformVersion}_${terraformPlatform.platform}.zip";
            inherit (terraformPlatform) sha256;
          };
          nativeBuildInputs = [ pkgs.unzip ];
          sourceRoot = ".";
          dontConfigure = true;
          dontBuild = true;
          installPhase = ''
            runHook preInstall
            install -Dm755 terraform "$out/bin/terraform"
            install -Dm644 LICENSE.txt "$out/share/licenses/terraform/LICENSE.txt"
            runHook postInstall
          '';
          meta = {
            description = "Terraform infrastructure as code CLI";
            homepage = "https://www.terraform.io/";
            license = pkgs.lib.licenses.bsl11;
            mainProgram = "terraform";
            platforms = builtins.attrNames terraformPlatforms;
          };
        };

        javaHook = ''export JAVA_HOME=${jdk}'';
        rustCoverageHook = ''
          export LLVM_COV=${pkgs.llvmPackages_21.llvm.out}/bin/llvm-cov
          export LLVM_PROFDATA=${pkgs.llvmPackages_21.llvm.out}/bin/llvm-profdata
        '';
      in
      {
        devShells = {
          infra = pkgs.mkShell {
            packages = [ terraform ];
          };
          # 全部入り（ローカル開発のデフォルト）
          default = pkgs.mkShell {
            packages = scalaPkgs ++ rustPkgs ++ nodePkgs;
            shellHook = ''
              ${javaHook}
              ${rustCoverageHook}
              echo "morecat dev shell (all)"
              echo "  java: $(${jdk}/bin/java -version 2>&1 | head -1)"
            '';
          };

          # CI/用途別: apps/api (Scala) だけ
          scala = pkgs.mkShell {
            packages = scalaPkgs;
            shellHook = javaHook;
          };

          # CI/用途別: apps/rmu (Rust) だけ
          rust = pkgs.mkShell {
            packages = rustPkgs;
            shellHook = ''
              ${javaHook}
              ${rustCoverageHook}
            '';
          };

          # CI/用途別: apps/ui (Node/pnpm) だけ
          node = pkgs.mkShell {
            packages = nodePkgs;
          };
        };
      });
}
