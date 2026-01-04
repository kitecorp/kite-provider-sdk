# Template Rendering Pattern

Use when generating HTML, XML, or other text-based output with embedded content that needs variable substitution.

## When to Use

- Generating HTML documentation pages
- Creating configuration files with dynamic values
- Any text generation where inline strings become unwieldy
- When the same template structure is used with different data

## Pattern

### 1. Create Template File

Place templates in `src/main/resources/` with `{{PLACEHOLDER}}` syntax:

```html
<!-- src/main/resources/templates/example.html -->
<!DOCTYPE html>
<html>
<head>
    <title>{{TITLE}}</title>
</head>
<body>
    <h1>{{HEADING}}</h1>
    <p>{{CONTENT}}</p>
</body>
</html>
```

### 2. Add Rendering Methods

```java
private String readResource(String path) {
    try (var is = getClass().getResourceAsStream(path)) {
        if (is == null) throw new IllegalStateException("Resource not found: " + path);
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
        throw new IllegalStateException("Failed to read resource: " + path, e);
    }
}

private String render(String template, Map<String, String> vars) {
    var result = template;
    for (var entry : vars.entrySet()) {
        result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    return result;
}

private String renderTemplate(String templatePath, Map<String, String> vars) {
    return render(readResource(templatePath), vars);
}
```

### 3. Use Templates

```java
var html = renderTemplate("/templates/example.html", Map.of(
    "TITLE", "My Page",
    "HEADING", "Welcome",
    "CONTENT", "Hello world"
));
```

## Benefits

- **Separation of concerns**: Templates are plain HTML/text files, editable without Java recompilation
- **Readability**: No escaping quotes or concatenating strings
- **Maintainability**: Designers can edit templates directly
- **Testability**: Templates can be validated independently

## Guidelines

1. Use UPPERCASE for placeholder names to distinguish from content
2. Keep templates in a dedicated subdirectory (e.g., `/templates/`)
3. Escape HTML in values using a utility method when needed
4. For complex logic, consider a library like Mustache or Thymeleaf

## Reference Implementation

See `HtmlDocGenerator.java` which uses this pattern for documentation generation:
- Templates: `src/main/resources/docgen/templates/`
- Methods: `render()`, `renderTemplate()`, `readResource()`