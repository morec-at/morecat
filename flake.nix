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
        pkgs = import nixpkgs { inherit system; };
        # Scala 3.8.x のコンパイラブリッジは JDK 17+ を要求するため 25 を固定。
        jdk = pkgs.jdk25;
        sbt = pkgs.sbt.override { jre = jdk; };

        scalaPkgs = [ jdk sbt pkgs.coursier ]; # apps/api
        rustPkgs = [ pkgs.cargo pkgs.rustc pkgs.rustfmt pkgs.clippy ]; # apps/rmu
        nodePkgs = [ pkgs.nodejs_22 ]; # apps/ui

        javaHook = ''export JAVA_HOME=${jdk}'';
      in
      {
        devShells = {
          # 全部入り（ローカル開発のデフォルト）
          default = pkgs.mkShell {
            packages = scalaPkgs ++ rustPkgs ++ nodePkgs;
            shellHook = ''
              ${javaHook}
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
          };
        };
      });
}
