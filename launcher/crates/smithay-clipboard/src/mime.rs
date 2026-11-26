pub static ALLOWED_MIME_TYPES: [&str; 3] =
    ["text/plain;charset=utf-8", "UTF8_STRING", "text/plain"];

#[derive(Clone, Copy, Eq, PartialEq, Debug)]
pub enum MimeType {
    TextPlainUtf8 = 0,
    Utf8String = 1,
    TextPlain = 2,
}

impl MimeType {
    pub fn find_allowed(offered_mime_types: &[String]) -> Option<Self> {
        let mut fallback = None;
        for offered_mime_type in offered_mime_types.iter() {
            if offered_mime_type == ALLOWED_MIME_TYPES[Self::TextPlainUtf8 as usize] {
                return Some(Self::TextPlainUtf8);
            } else if offered_mime_type == ALLOWED_MIME_TYPES[Self::Utf8String as usize] {
                return Some(Self::Utf8String);
            } else if offered_mime_type == ALLOWED_MIME_TYPES[Self::TextPlain as usize] {
                fallback = Some(Self::TextPlain);
            }
        }

        fallback
    }
}

impl std::fmt::Display for MimeType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", ALLOWED_MIME_TYPES[*self as usize])
    }
}

pub fn normalize_to_lf(text: String) -> String {
    text.replace("\r\n", "\n").replace('\r', "\n")
}
