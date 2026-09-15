package adris.altoclef.tasks.speedrun.testrun2.gui;

/** Built-in chat APIs. Key stays in AgentConfig; this only fills url + model. */
public final class AgentPresets {

    public static final class Preset {
        public final String id;
        public final String label;
        public final String url;
        public final String[] models;

        Preset(String id, String label, String url, String... models) {
            this.id = id;
            this.label = label;
            this.url = url;
            this.models = models;
        }
    }

    public static final Preset[] ALL = {
            new Preset("xai", "xAI Grok",
                    "https://api.x.ai/v1/chat/completions",
                    "grok-4", "grok-3", "grok-2"),
            new Preset("openai", "OpenAI",
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-4o", "gpt-4.1", "o4-mini"),
            new Preset("anthropic", "Anthropic",
                    "https://api.anthropic.com/v1/messages",
                    "claude-sonnet-4-5", "claude-opus-4", "claude-3-5-haiku-latest"),
            new Preset("openrouter", "OpenRouter",
                    "https://openrouter.ai/api/v1/chat/completions",
                    "x-ai/grok-4", "openai/gpt-4o", "anthropic/claude-sonnet-4.5"),
            new Preset("ollama", "Ollama local",
                    "http://127.0.0.1:11434/v1/chat/completions",
                    "llama3.1", "qwen2.5", "mistral"),
            new Preset("gemini", "Google Gemini",
                    "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
                    "gemini-2.5-pro", "gemini-2.5-flash"),
            new Preset("custom", "Custom URL",
                    "",
                    "custom-model"),
    };

    private AgentPresets() {}

    public static Preset byId(String id) {
        if (id == null) return ALL[0];
        for (Preset p : ALL) {
            if (p.id.equalsIgnoreCase(id)) return p;
        }
        return ALL[0];
    }

    public static Preset byLabel(String label) {
        if (label == null) return ALL[0];
        for (Preset p : ALL) {
            if (p.label.equalsIgnoreCase(label) || p.id.equalsIgnoreCase(label)) return p;
        }
        return ALL[0];
    }
}
