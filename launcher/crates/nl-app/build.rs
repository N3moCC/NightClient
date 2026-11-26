fn main() {
    println!("cargo:rerun-if-changed=resources.rc");
    println!("cargo:rerun-if-changed=../../assets/nightclient-icon.ico");
    println!("cargo:rerun-if-changed=../../assets/nightclient-icon.png");
    println!("cargo:rerun-if-changed=../../assets/nightclient-logo.svg");

    let target_os = std::env::var("CARGO_CFG_TARGET_OS").unwrap_or_default();
    if target_os == "windows" {
        embed_resource::compile("resources.rc", embed_resource::NONE);
    }
}
