// Generated from app/src/main/kotlin/io/github/mangi/eta/data/provider/OfficialModelCatalog.kt @ 3b1794d; run scripts/port-eta-official-models.py.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
import type { ModelMetadata } from "../modelMetadata";
export const OFFICIAL_MODELS: Record<string, ModelMetadata[]> = {
  "openai": [
    {
      "catalogId": "builtin-openai-gpt-6-1-sol",
      "id": "gpt-6.1-sol",
      "name": "GPT-6.1 Sol",
      "ownedBy": "openai",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1050000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ],
      "contextWindow": 1050000
    },
    {
      "catalogId": "builtin-openai-gpt-6-luna",
      "id": "gpt-6-luna",
      "name": "GPT-6 Luna",
      "ownedBy": "openai",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1050000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ],
      "contextWindow": 1050000
    },
    {
      "catalogId": "builtin-openai-gpt-6-astra",
      "id": "gpt-6-astra",
      "name": "GPT-6 Astra",
      "ownedBy": "openai",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1050000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ],
      "contextWindow": 1050000
    }
  ],
  "gemini": [
    {
      "catalogId": "builtin-gemini-3-8-flash",
      "id": "gemini-3.8-flash",
      "name": "Gemini 3.8 Flash",
      "ownedBy": "google",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1048576,
      "source": "catalog",
      "outputModalities": [
        "text"
      ],
      "contextWindow": 1048576
    },
    {
      "catalogId": "builtin-gemini-4-argon",
      "id": "gemini-4-argon",
      "name": "Gemini 4 Argon",
      "ownedBy": "google",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1048576,
      "source": "catalog",
      "outputModalities": [
        "text"
      ],
      "contextWindow": 1048576
    },
    {
      "catalogId": "builtin-gemini-3-5-flash-lite",
      "id": "gemini-3.5-flash-lite",
      "name": "Gemini 3.5 Flash-Lite",
      "ownedBy": "google",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1048576,
      "source": "catalog",
      "outputModalities": [
        "text"
      ],
      "contextWindow": 1048576
    }
  ],
  "anthropic": [
    {
      "catalogId": "builtin-anthropic-claude-fable-5-1",
      "id": "claude-fable-5-1",
      "name": "Claude Fable 5.1",
      "ownedBy": "anthropic",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-anthropic-claude-opus-5-5",
      "id": "claude-opus-5-5",
      "name": "Claude Opus 5.5",
      "ownedBy": "anthropic",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-anthropic-claude-fable-5",
      "id": "claude-fable-5",
      "name": "Claude Fable 5",
      "ownedBy": "anthropic",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-anthropic-claude-opus-4-8",
      "id": "claude-opus-4-8",
      "name": "Claude Opus 4.8",
      "ownedBy": "anthropic",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-anthropic-claude-sonnet-5",
      "id": "claude-sonnet-5",
      "name": "Claude Sonnet 5",
      "ownedBy": "anthropic",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ],
  "bailian": [
    {
      "catalogId": "builtin-bailian-qwen3-8-max",
      "id": "qwen3.8-max",
      "name": "Qwen3.8 Max",
      "ownedBy": "qwen",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-bailian-qwen3-8-flash",
      "id": "qwen3.8-flash",
      "name": "Qwen3.8 Flash",
      "ownedBy": "qwen",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-bailian-qwen3-7-plus",
      "id": "qwen3.7-plus",
      "name": "Qwen3.7 Plus",
      "ownedBy": "qwen",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-bailian-deepseek-v4-1-flash",
      "id": "deepseek-v4.1-flash",
      "name": "DeepSeek V4.1 Flash",
      "ownedBy": "deepseek",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-bailian-kimi-k3",
      "id": "kimi-k3",
      "name": "Kimi K3",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-bailian-kimi-k2-7-code",
      "id": "kimi-k2.7-code",
      "name": "Kimi K2.7 Code",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 256000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-bailian-kimi-k2-6",
      "id": "kimi-k2.6",
      "name": "Kimi K2.6",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 256000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ],
  "deepseek": [
    {
      "catalogId": "builtin-deepseek-v4-pro",
      "id": "deepseek-v4-pro",
      "name": "DeepSeek V4 Pro",
      "ownedBy": "deepseek",
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1048576,
      "source": "catalog",
      "inputModalities": [
        "text"
      ],
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-deepseek-flash",
      "id": "deepseek-flash",
      "name": "DeepSeek Flash",
      "ownedBy": "deepseek",
      "inputModalities": [
        "text",
        "image"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1048576,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ],
  "moonshot": [
    {
      "catalogId": "builtin-kimi-k3",
      "id": "kimi-k3",
      "name": "Kimi K3",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1048576,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-kimi-k2-7-code",
      "id": "kimi-k2.7-code",
      "name": "Kimi K2.7 Code",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 256000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-kimi-k2-7-code-highspeed",
      "id": "kimi-k2.7-code-highspeed",
      "name": "Kimi K2.7 Code HighSpeed",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 256000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-kimi-k2-6",
      "id": "kimi-k2.6",
      "name": "Kimi K2.6",
      "ownedBy": "moonshot",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 256000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ],
  "mimo": [
    {
      "catalogId": "builtin-mimo-v2-6-pro",
      "id": "mimo-v2.6-pro",
      "name": "MiMo V2.6 Pro",
      "ownedBy": "xiaomi",
      "inputModalities": [
        "text",
        "image",
        "video",
        "audio"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-mimo-v2-6-flash",
      "id": "mimo-v2.6-flash",
      "name": "MiMo V2.6 Flash",
      "ownedBy": "xiaomi",
      "inputModalities": [
        "text",
        "image",
        "video",
        "audio"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-mimo-v2-5-pro",
      "id": "mimo-v2.5-pro",
      "name": "MiMo V2.5 Pro",
      "ownedBy": "xiaomi",
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "inputModalities": [
        "text"
      ],
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-mimo-v2-5",
      "id": "mimo-v2.5",
      "name": "MiMo V2.5",
      "ownedBy": "xiaomi",
      "inputModalities": [
        "text",
        "image",
        "video",
        "audio"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ],
  "minimax": [
    {
      "catalogId": "builtin-minimax-m3",
      "id": "MiniMax-M3",
      "name": "MiniMax M3",
      "ownedBy": "minimax",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ],
  "stepfun": [
    {
      "catalogId": "builtin-stepfun-step-5-preview",
      "id": "step-5-preview",
      "name": "Step 5 Preview",
      "ownedBy": "stepfun",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "structuredOutput": true,
      "catalogContextWindow": 1000000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    },
    {
      "catalogId": "builtin-stepfun-step-3-7-flash",
      "id": "step-3.7-flash",
      "name": "Step 3.7 Flash",
      "ownedBy": "stepfun",
      "inputModalities": [
        "text",
        "image",
        "video"
      ],
      "toolCall": true,
      "reasoning": true,
      "catalogContextWindow": 256000,
      "source": "catalog",
      "outputModalities": [
        "text"
      ]
    }
  ]
};
