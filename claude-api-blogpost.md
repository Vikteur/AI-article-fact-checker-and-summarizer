# Building with the Claude API: A Practical Introduction

> **TL;DR:** The Claude API lets you embed AI into your own applications with a single HTTP call. In this post I'll explain what it is, why it's useful, and walk you through two real Java projects you can run on your own server: a simple text summarizer, and a multi-agent pipeline where three specialized AI agents collaborate to produce a polished news briefing.

---

## What is the Claude API?

Claude is Anthropic's AI assistant. Most people know it as a chat interface at claude.ai, but behind that interface sits a powerful API that any developer can call directly.

The **Claude API** gives you programmatic access to Claude's language capabilities. Instead of you typing a question into a chat box, *your application* sends a request and gets an intelligent response back — automatically, at scale.

Think of it like this: every time you use a smart feature in a modern app — an AI writing assistant, an automatic email categorizer, a document Q&A tool — there's a language model API behind the scenes doing the heavy lifting. The Claude API is that layer.

---

## Why would you use it?

Here's the honest pitch: writing software that *understands text* used to be incredibly hard. You'd need a machine learning team, months of training data, and expensive infrastructure.

With the Claude API, you skip all of that. You describe what you want in plain English (your "prompt"), send the text you want processed, and get an intelligent result back. The whole thing is an HTTP POST request.

Some concrete use cases:

- **Summarize** long documents, emails, or reports automatically
- **Classify** incoming support tickets by urgency or topic
- **Extract** structured data from unstructured text (e.g., pull dates and names from a contract)
- **Generate** first drafts of emails, product descriptions, or release notes
- **Answer questions** about your own documents (a personal knowledge base)
- **Explain** complex topics in simple language (think: a help-desk bot that speaks human)

The common thread: anything that a smart person could do by *reading and understanding text*, Claude can do at scale and on demand.

---

## How does it work?

The API follows a simple request-response model. You send a JSON body to `https://api.anthropic.com/v1/messages` and get a JSON response back.

**A minimal request looks like this:**

```json
POST https://api.anthropic.com/v1/messages
Headers:
  x-api-key: YOUR_API_KEY
  anthropic-version: 2023-06-01
  Content-Type: application/json

Body:
{
  "model": "claude-3-5-haiku-20241022",
  "max_tokens": 1024,
  "messages": [
    {
      "role": "user",
      "content": "Summarize this email in 3 bullet points: [email text here]"
    }
  ]
}
```

**And the response:**

```json
{
  "content": [
    {
      "type": "text",
      "text": "• The meeting is rescheduled to Thursday at 2pm\n• The client wants a revised proposal by EOW\n• Action item: Viktor to send updated pricing"
    }
  ]
}
```

That's genuinely it. No fine-tuning, no embeddings, no model hosting. You write a good prompt, make an HTTP call, and use the result.

### The three things you control

| Parameter | What it does | Typical value |
|---|---|---|
| `model` | Which Claude model to use | `claude-3-5-haiku-20241022` (fast & cheap) or `claude-sonnet-4-5` (smarter) |
| `max_tokens` | Max length of the response | 256–4096 depending on your use case |
| `messages` | The conversation — your prompt goes here | Array of `{ role, content }` objects |

---

## Let's build something: a Smart Text Summarizer

Enough theory. Let's build a real, runnable service.

**The idea:** A REST API endpoint that accepts any block of text — an email, a news article, a meeting transcript — and returns a concise bullet-point summary. You could plug this into a browser extension, a Slack bot, an internal dashboard, anything.

**Stack:** Java 17 + Spring Boot 3

### Project structure

```
claude-summarizer/
├── pom.xml
└── src/main/
    ├── java/com/example/summarizer/
    │   ├── SummarizerApplication.java   ← Spring Boot entry point
    │   ├── SummarizerController.java    ← REST endpoint
    │   ├── ClaudeService.java           ← All Claude API logic lives here
    │   ├── SummarizeRequest.java        ← Request body (the text)
    │   └── SummarizeResponse.java       ← Response body (the summary)
    └── resources/
        └── application.properties       ← Config (API key, model)
```

### Step 1 — The Maven dependencies (`pom.xml`)

We only need two Spring Boot starters: `spring-boot-starter-web` for the REST layer and `spring-boot-starter-webflux` for the HTTP client (`WebClient`) that calls the Claude API.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

No exotic libraries. No Claude SDK. Just plain HTTP — which means you can apply exactly the same pattern in any language.

### Step 2 — Configuration (`application.properties`)

```properties
server.port=8080

# Load your API key from an environment variable (never hardcode it!)
claude.api.key=${CLAUDE_API_KEY:your-api-key-here}
claude.api.url=https://api.anthropic.com/v1/messages
claude.model=claude-3-5-haiku-20241022
```

Set your key before running:

```bash
export CLAUDE_API_KEY=sk-ant-xxxxxxxx
```

> 💡 **Get your API key** at [console.anthropic.com](https://console.anthropic.com). New accounts get free credits to experiment with.

### Step 3 — The Claude service (the interesting part)

This is where the magic happens. The entire integration with Claude is about 40 lines:

```java
@Service
public class ClaudeService {

    private final WebClient webClient;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    public ClaudeService(@Value("${claude.api.url}") String apiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String summarize(String text) {

        // 1. Write the prompt
        String prompt = """
                Please summarize the following text in 3-5 bullet points.
                Be concise and focus on the most important information.

                Text to summarize:
                ---
                %s
                ---
                """.formatted(text);

        // 2. Build the request body
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        // 3. POST to Claude API
        Map<String, Object> response = webClient.post()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        // 4. Extract the text from the response
        List<Map<String, Object>> content =
                (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }
}
```

Notice how readable this is. The prompt is just a string. The request body is a plain `Map`. There's no framework magic — it's the same call you'd make with `curl`.

### Step 4 — The REST controller

```java
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SummarizerController {

    private final ClaudeService claudeService;

    public SummarizerController(ClaudeService claudeService) {
        this.claudeService = claudeService;
    }

    @PostMapping("/summarize")
    public ResponseEntity<SummarizeResponse> summarize(@RequestBody SummarizeRequest request) {

        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new SummarizeResponse("Please provide some text to summarize."));
        }

        String summary = claudeService.summarize(request.getText());
        return ResponseEntity.ok(new SummarizeResponse(summary));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Claude Summarizer is running!");
    }
}
```

Clean, standard Spring Boot. Nothing Claude-specific here — the controller has no idea how the summary is generated. That's good design.

---

## Running it

```bash
# 1. Set your API key
export CLAUDE_API_KEY=sk-ant-xxxxxxxx

# 2. Build and run
./mvnw spring-boot:run

# 3. Test it
curl -X POST http://localhost:8080/api/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "text": "The quarterly earnings call revealed that revenue grew 23% year-over-year, driven primarily by strong performance in the cloud division. However, operating margins contracted by 2 percentage points due to increased R&D investment. The CEO emphasized that the company plans to expand into three new markets in Southeast Asia during Q3. Headcount grew by 15% and the company expects to hire an additional 500 engineers by year end. Free cash flow remained strong at $2.1 billion."
  }'
```

**Response:**

```json
{
  "summary": "• Revenue grew 23% YoY, driven by the cloud division\n• Operating margins contracted 2pp due to higher R&D spend\n• Expansion into 3 Southeast Asian markets planned for Q3\n• Headcount up 15%, with 500 more engineers to be hired by year end\n• Free cash flow strong at $2.1 billion"
}
```

That's a real API call, with a real response, from a real Claude model — in about 1–2 seconds.

---

## What makes this pattern powerful

The summarizer is a toy example, but the pattern scales to real products:

**Change the prompt, change the behaviour.** Want to extract action items instead of bullet points? Change two lines of the prompt. Want to translate the summary into French? Add one sentence. The entire "intelligence" of your feature is the prompt — no redeployment of a model, no retraining.

**It composes.** You can chain calls. Summarize a document, then classify the summary, then generate a response email — each step is one API call.

**It's fast to ship.** The gap between "I have an idea for an AI feature" and "it's running in production" is now measured in hours, not months.

---

## Pricing: is it expensive?

The model used in this demo (`claude-3-5-haiku-20241022`) is Anthropic's fastest and most affordable model. To give you a sense of scale:

- **1 million input tokens** costs around $0.80
- The average English paragraph is ~100 tokens
- Summarizing a 500-word email costs roughly **$0.0004** — less than a fraction of a cent

For internal tooling, prototypes, or low-to-medium traffic products, the cost is negligible. As you scale, you can optimize by caching results or batching requests.

---

## Where to go from here

The summarizer is a starting point. Here are three natural extensions to try once you've got it running:

**1. Add a system prompt** to give Claude a persona or enforce a specific output format:

```java
"messages", List.of(
    Map.of("role", "system", "content",
           "You are a concise executive assistant. Always respond in plain bullet points."),
    Map.of("role", "user", "content", prompt)
)
```

**2. Build a multi-turn conversation** by keeping the message history and appending each new user message to the array. This is how you build a chatbot.

**3. Combine with your own data** — pass in content from your database, files, or CRM alongside the user's question. This is the foundation of "chat with your documents" features.

---

---

## Going further: Multiple agents working together

The summarizer makes one API call. But what if you need something more sophisticated — multiple steps of reasoning, each handled by a specialized agent?

This is where **multi-agent pipelines** come in. The idea is simple: each agent is a Claude API call with a different *system prompt* that defines its role. The output of one agent becomes the input of the next.

### The News Briefing Pipeline

Let's extend the project with a second endpoint: `/api/briefing`. You feed it a raw news article. Three agents take it from there:

```
[Raw Article]
     ↓
Agent 1 — Summarizer   → "Extract the 4-6 key facts. Nothing else."
     ↓
Agent 2 — Analyst      → "Given these facts, assess sentiment and list implications."
     ↓
Agent 3 — Editor       → "Format facts + analysis into an executive briefing."
     ↓
[Final Briefing]
```

Each agent is focused and knows only its piece. The Analyst doesn't read the full article — it reads the Summarizer's output. The Editor doesn't do any analysis — it formats what the Analyst produced. This separation keeps each agent sharp and its output predictable.

### The key insight: system prompts define agents

In the Claude API, a `system` field sets the agent's "personality" and job description before any user message is processed. This is what makes each call behave as a different specialist:

```java
// Agent 1: Summarizer
String summarizerSystem = """
    You are a precise fact extractor. Your only job is to read a piece
    of text and extract the 4-6 most important factual statements.
    Output ONLY the facts as a numbered list. No opinions, no filler.
    """;

// Agent 2: Analyst
String analystSystem = """
    You are a sharp business analyst. Given a list of facts, you will:
    1. Identify the overall sentiment (Positive / Neutral / Negative) and explain why.
    2. List 2-3 key business or practical implications.
    Keep it concise and direct.
    """;

// Agent 3: Editor
String editorSystem = """
    You are a professional editor creating executive briefings.
    Combine facts and analysis into a structured briefing with:
      📋 Summary (2 sentences max)
      📊 Key Facts (bullet list)
      🔍 Analysis (sentiment + implications)
      ⚡ Bottom Line (one sentence takeaway)
    """;
```

Same API. Same model. Three completely different behaviours — defined entirely by those strings.

### The pipeline implementation

The `AgentPipelineService` wires the three agents together. Each one is powered by the same `callAgent()` helper:

```java
private String callAgent(String systemPrompt, String userMessage) {
    Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", 1024,
            "messages", List.of(
                    Map.of("role", "user", "content", userMessage)
            ),
            "system", systemPrompt   // ← This is what makes each agent unique
    );

    Map<String, Object> response = webClient.post()
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(Map.class)
            .block();

    List<Map<String, Object>> content =
            (List<Map<String, Object>>) response.get("content");
    return (String) content.get(0).get("text");
}
```

And the pipeline chains them:

```java
public BriefingResult runPipeline(String articleText) {

    // Agent 1 reads the raw article
    String facts = callAgent(summarizerSystem,
            "Extract the key facts from this article:\n\n" + articleText);

    // Agent 2 reads Agent 1's output — NOT the original article
    String analysis = callAgent(analystSystem,
            "Analyse these extracted facts:\n\n" + facts);

    // Agent 3 receives both previous outputs
    String briefing = callAgent(editorSystem,
            "FACTS:\n" + facts + "\n\nANALYSIS:\n" + analysis);

    return new BriefingResult(facts, analysis, briefing);
}
```

### Trying it out

```bash
curl -X POST http://localhost:8080/api/briefing \
  -H "Content-Type: application/json" \
  -d '{
    "article": "Anthropic today announced a significant expansion of its enterprise offerings, including new API rate limits and a dedicated compliance tier for regulated industries. The company reported that API usage has tripled over the past six months, driven largely by adoption in financial services and healthcare. CEO Dario Amodei stated that safety and reliability remain the company'\''s top priorities as it scales. The new enterprise tier includes SLA guarantees, dedicated infrastructure, and priority support. Pricing starts at $50,000 per year for qualifying organizations."
  }'
```

**Response — you can see exactly what each agent produced:**

```json
{
  "stage_1_facts": "1. Anthropic expanded its enterprise API offerings\n2. New compliance tier added for regulated industries\n3. API usage tripled in 6 months, led by finance and healthcare\n4. New tier includes SLAs, dedicated infra, and priority support\n5. Enterprise pricing starts at $50,000/year",

  "stage_2_analysis": "Sentiment: Positive — strong growth signals and strategic positioning in high-value sectors.\nImplications:\n• Anthropic is moving up-market, targeting large regulated enterprises willing to pay premium prices\n• Tripling of API usage validates product-market fit and suggests accelerating enterprise adoption\n• The compliance tier positions Anthropic as a serious competitor in sectors where reliability and security are non-negotiable",

  "stage_3_briefing": "📋 Summary\nAnthropic is expanding aggressively into enterprise, launching a compliance-grade API tier aimed at finance and healthcare clients. API usage has tripled in six months, signalling strong demand.\n\n📊 Key Facts\n• API usage 3x in 6 months — finance and healthcare leading\n• New enterprise tier: SLAs, dedicated infrastructure, priority support\n• Enterprise pricing starts at $50,000/year\n• New compliance tier targets regulated industries\n\n🔍 Analysis\nSentiment: Positive. Anthropic is executing a clear up-market move.\nKey implications: premium pricing validates confidence in product quality; regulated-sector focus differentiates from competitors; growth trajectory suggests the transition from research lab to enterprise vendor is well underway.\n\n⚡ Bottom Line\nAnthropic is turning its API into an enterprise platform — and the market is responding."
}
```

Three agents, three API calls, one coherent result. The magic is in how cleanly each agent's output hands off to the next.

### Why this pattern matters

Multi-agent pipelines let you break complex tasks into steps that are individually verifiable. You can inspect `stage_1_facts` and check whether the Summarizer did its job before the Analyst builds on it. If one stage produces bad output, you know exactly which agent to fix (i.e., which prompt to tune), without touching the others.

It also scales horizontally. Need a translation agent at the end? Add one call. Need a compliance check before the Editor runs? Insert an agent between steps 2 and 3. The architecture is a pipeline — extending it is additive, not disruptive.

---

## Wrapping up

The Claude API removes the hardest part of building AI features: the model itself. What's left is the part developers are already good at — structuring data, writing prompts, and wiring things together with HTTP calls.

The two examples in this post are intentionally small. The single-agent summarizer shows the core integration is just 40 lines of Java. The multi-agent pipeline shows that orchestrating multiple AI specialists is just a matter of chaining those calls — still no exotic frameworks, no complicated setup. Once that foundation is in place, the architecture scales naturally: add more agents for more steps, add branching logic to pick different agents based on content type, or run agents in parallel for independent sub-tasks.

If you want to experiment, grab an API key at [console.anthropic.com](https://console.anthropic.com), clone the project, and run it locally. The full source code is available alongside this article.

Happy building.

---

*The full project source (ready to run) is included with this post.*
