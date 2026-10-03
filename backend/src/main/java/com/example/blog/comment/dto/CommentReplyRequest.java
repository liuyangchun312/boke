package com.example.blog.comment.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.IOException;

public record CommentReplyRequest(
        @NotNull @Size(max = 1000) @JsonDeserialize(using = CommentReplyRequest.ContentDeserializer.class) String content) {
    public static final class ContentDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getText();
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
    }
}
