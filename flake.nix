{
  description = "morecat development environment";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/cf5e76507c6e23b59f7e0ffcc7baa2a39ddd8442";

  outputs = { nixpkgs, ... }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
    in {
      devShells = nixpkgs.lib.genAttrs systems (system:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in {
          default = pkgs.mkShellNoCC {
            packages = [ pkgs.nodejs_24 pkgs.pnpm ];
          };
        });
    };
}
