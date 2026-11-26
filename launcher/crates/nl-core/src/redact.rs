use regex::Regex;
use std::sync::OnceLock;

pub fn redact_log_line(line: &str) -> String {
    static ACCESS_TOKEN_ARG: OnceLock<Regex> = OnceLock::new();
    static BEARER_RE: OnceLock<Regex> = OnceLock::new();
    static JWT_RE: OnceLock<Regex> = OnceLock::new();
    static SESSION_RE: OnceLock<Regex> = OnceLock::new();

    let re_arg = ACCESS_TOKEN_ARG.get_or_init(|| {
        Regex::new(r"(?i)(--accessToken\s+|accessToken[=:]\s*)(\S+)").unwrap()
    });
    let re_bearer = BEARER_RE.get_or_init(|| {
        Regex::new(r"(?i)(Bearer\s+)([A-Za-z0-9\-_\.=]+)").unwrap()
    });
    let re_jwt = JWT_RE.get_or_init(|| {
        Regex::new(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}").unwrap()
    });
    let re_session = SESSION_RE.get_or_init(|| {
        Regex::new(r"(?i)(token:)([A-Za-z0-9\-_\.]+)(:[A-Za-z0-9\-]+)").unwrap()
    });

    let step1 = re_arg.replace_all(line, "${1}[REDACTED]");
    let step2 = re_bearer.replace_all(&step1, "${1}[REDACTED]");
    let step3 = re_jwt.replace_all(&step2, "[REDACTED_JWT]");
    re_session.replace_all(&step3, "${1}[REDACTED]${3}").into_owned()
}

pub fn redact_log_text(text: &str) -> String {
    text.lines()
        .map(redact_log_line)
        .collect::<Vec<_>>()
        .join("\n")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn redacts_tokens_and_jwts() {
        let sample = "Launching with --accessToken eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature123456 --username Steve";
        let clean = redact_log_line(sample);
        assert!(!clean.contains("signature123456"));
        assert!(clean.contains("--accessToken [REDACTED]"));
        assert!(clean.contains("--username Steve"));

        let bearer = "Authorization: Bearer super_secret_token_999";
        assert_eq!(redact_log_line(bearer), "Authorization: Bearer [REDACTED]");
    }
}
