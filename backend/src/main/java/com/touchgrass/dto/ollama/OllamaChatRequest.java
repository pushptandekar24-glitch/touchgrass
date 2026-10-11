package com.touchgrass.dto.ollama;

import java.util.List;
import java.util.Map;

public class OllamaChatRequest {

    private String model;
    private List<OllamaMessage> messages;
    private Boolean stream = false;
    private Object format;
    private Map<String, Object> options;

    public OllamaChatRequest() {
    }

    public OllamaChatRequest(String model, List<OllamaMessage> messages, Object format, Map<String, Object> options) {
        this.model = model;
        this.messages = messages;
        this.stream = false;
        this.format = format;
        this.options = options;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<OllamaMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<OllamaMessage> messages) {
        this.messages = messages;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Object getFormat() {
        return format;
    }

    public void setFormat(Object format) {
        this.format = format;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public static class OllamaMessage {
        private String role;
        private String content;

        public OllamaMessage() {
        }

        public OllamaMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
