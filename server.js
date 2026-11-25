import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import { GoogleGenAI } from '@google/genai';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json({ limit: '25mb' }));

let geminiClient = null;
function getGeminiClient() {
  if (!geminiClient && process.env.GEMINI_API_KEY) {
    geminiClient = new GoogleGenAI({
      apiKey: process.env.GEMINI_API_KEY,
      httpOptions: {
        headers: {
          'User-Agent': 'aistudio-build',
        },
      },
    });
  }
  return geminiClient;
}

function formatCleanAiError(err) {
  let raw = err?.message || String(err || 'Unknown AI error');
  try {
    const parsed = JSON.parse(raw);
    if (parsed?.error?.message) raw = parsed.error.message;
  } catch {}
  if (raw.includes('API_KEY_INVALID') || raw.includes('API key not valid') || raw.includes('403') || raw.includes('PERMISSION_DENIED')) {
    return {
      status: 400,
      code: 'API_KEY_INVALID',
      error: 'Gemini API key is invalid or missing permissions. Check GEMINI_API_KEY in Settings > Secrets.',
    };
  }
  if (raw.includes('RESOURCE_EXHAUSTED') || raw.includes('429')) {
    return {
      status: 429,
      code: 'RATE_LIMITED',
      error: 'AI quota reached. Please wait a moment or check billing in Settings > Secrets.',
    };
  }
  return {
    status: 500,
    code: 'AI_ERROR',
    error: raw.slice(0, 220),
  };
}

function normalizeGeminiHistory(history) {
  if (!Array.isArray(history)) return [];
  const cleaned = [];
  for (const msg of history.slice(-10)) {
    if (!msg || !msg.text || msg.isFallback || msg.text === 'Thinking...') continue;
    const role = msg.role === 'model' ? 'model' : 'user';
    // Gemini generateContent contents must begin with a user turn
    if (cleaned.length === 0 && role === 'model') continue;
    cleaned.push({
      role,
      parts: [{ text: String(msg.text) }],
    });
  }
  return cleaned;
}

async function handleAiRequest(req, res) {
  res.set({
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
  });

  try {
    const { persona, question, topic, context, history } = req.body || {};
    if (!question || !String(question).trim()) {
      return res.status(400).json({ error: 'Please enter a question.' });
    }

    const isBuddy = persona !== 'teacher';
    const topicLine = topic ? `Current topic: ${topic}.` : 'Current topic: General Math Lab.';
    const contextLine = context ? `Live state in app: ${context}.` : '';
    const systemPrompt = isBuddy
      ? `You are Wave Buddy, a warm, encouraging, sharp AI math companion inside Wavelord Math Lab. You help with math, science, homework, formulas, and quick puzzles. Keep replies clear and concise (2-5 short sentences or clean bullet steps) so they read well inside a floating mobile overlay. Use real-world examples and show step-by-step working when asked. ${topicLine} ${contextLine}`
      : `You are the Teacher in Wavelord Math Lab. Explain clearly with step-by-step working, one real-life example, and one short try-this prompt. ${topicLine} ${contextLine}`;

    let lastGeminiErr = null;

    if (process.env.GEMINI_API_KEY) {
      try {
        const ai = getGeminiClient();
        const contents = normalizeGeminiHistory(history);
        contents.push({
          role: 'user',
          parts: [{ text: String(question).trim() }],
        });

        const response = await ai.models.generateContent({
          model: 'gemini-4-hydrogen',
          contents,
          config: {
            systemInstruction: systemPrompt,
          },
        });

        const answer = response.text?.trim() || "I'm ready — ask me another math question!";
        return res.status(200).json({ answer, provider: 'gemini' });
      } catch (err) {
        lastGeminiErr = err;
      }
    }

    if (process.env.GROQ_API_KEY) {
      const messages = [{ role: 'system', content: systemPrompt }];
      if (Array.isArray(history)) {
        for (const msg of history.slice(-8)) {
          if (!msg || !msg.text || msg.isFallback || msg.text === 'Thinking...') continue;
          messages.push({
            role: msg.role === 'model' ? 'assistant' : 'user',
            content: String(msg.text),
          });
        }
      }
      messages.push({ role: 'user', content: String(question).trim() });

      const apiRes = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${process.env.GROQ_API_KEY}`,
        },
        body: JSON.stringify({
          model: 'openai/gpt-oss-20b',
          messages,
          temperature: 0.7,
          max_tokens: 800,
        }),
      });

      const data = await apiRes.json();
      if (!apiRes.ok) {
        return res.status(apiRes.status).json({
          error: 'Groq error: ' + (data?.error?.message || apiRes.status),
        });
      }
      const answer = data?.choices?.[0]?.message?.content || "I'm quiet right now, try again.";
      return res.status(200).json({ answer, provider: 'groq' });
    }

    if (lastGeminiErr) {
      const clean = formatCleanAiError(lastGeminiErr);
      return res.status(clean.status).json({ error: clean.error, code: clean.code });
    }

    return res.status(503).json({
      code: 'NO_API_KEY',
      error: 'No AI API key configured. Add GEMINI_API_KEY in Settings > Secrets.',
    });
  } catch (e) {
    const clean = formatCleanAiError(e);
    return res.status(clean.status).json({ error: clean.error, code: clean.code });
  }
}

app.options(['/api/ai', '/.netlify/functions/ai', '/api/transcribe', '/api/tts'], (req, res) => {
  res.set({
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type',
  });
  res.status(200).send('');
});

app.post(['/api/ai', '/.netlify/functions/ai'], handleAiRequest);

// Voice-to-Text Transcription via Gemini 3.5 Transcribe (with optional Groq Whisper fallback)
app.post('/api/transcribe', async (req, res) => {
  try {
    const { audioBase64, mimeType } = req.body || {};
    if (!audioBase64) {
      return res.status(400).json({ error: 'No audio data received.' });
    }

    let lastGeminiErr = null;
    const cleanMime = String(mimeType || 'audio/webm').split(';')[0].trim().toLowerCase();
    const geminiMime = cleanMime === 'audio/opus' ? 'audio/ogg' : cleanMime;
    if (process.env.GEMINI_API_KEY) {
      try {
        const ai = getGeminiClient();
        const audioPart = {
          inlineData: {
            mimeType: geminiMime,
            data: audioBase64,
          },
        };
        const response = await ai.models.generateContent({
          model: 'gemini-3.5-transcribe',
          contents: {
            parts: [
              audioPart,
              { text: 'Transcribe this spoken voice note accurately into clean plain text. Return only the transcribed words.' },
            ],
          },
        });

        const text = (response.text || '').trim();
        return res.status(200).json({ text, provider: 'gemini' });
      } catch (err) {
        lastGeminiErr = err;
      }
    }

    if (process.env.GROQ_API_KEY) {
      const buf = Buffer.from(audioBase64, 'base64');
      const ext = (cleanMime.includes('mp4') || cleanMime.includes('m4a') || cleanMime.includes('aac')) ? 'm4a'
        : (cleanMime.includes('mpeg') || cleanMime.includes('mp3')) ? 'mp3'
        : (cleanMime.includes('ogg') || cleanMime.includes('opus')) ? 'ogg'
        : cleanMime.includes('wav') ? 'wav'
        : 'webm';
      const form = new FormData();
      form.append('file', new Blob([buf], { type: geminiMime }), `voicenote.${ext}`);
      form.append('model', 'whisper-large-v3-turbo');

      const groqRes = await fetch('https://api.groq.com/openai/v1/audio/transcriptions', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${process.env.GROQ_API_KEY}`,
        },
        body: form,
      });
      const groqData = await groqRes.json();
      if (!groqRes.ok) {
        return res.status(groqRes.status).json({
          error: 'Transcription error: ' + (groqData?.error?.message || groqRes.status),
        });
      }
      return res.status(200).json({ text: (groqData?.text || '').trim(), provider: 'groq' });
    }

    if (lastGeminiErr) {
      const clean = formatCleanAiError(lastGeminiErr);
      return res.status(clean.status).json({ error: clean.error, code: clean.code });
    }

    return res.status(503).json({
      code: 'NO_API_KEY',
      error: 'GEMINI_API_KEY is required for server audio transcription.',
    });
  } catch (e) {
    const clean = formatCleanAiError(e);
    return res.status(clean.status).json({ error: clean.error, code: clean.code });
  }
});

// Read-Aloud TTS via Gemini 3.8 Flash Lite TTS
app.post('/api/tts', async (req, res) => {
  try {
    const { text } = req.body || {};
    if (!text || !String(text).trim()) {
      return res.status(400).json({ error: 'No text provided.' });
    }
    if (!process.env.GEMINI_API_KEY) {
      return res.status(503).json({
        code: 'NO_API_KEY',
        error: 'GEMINI_API_KEY is required for Gemini TTS.',
      });
    }

    const ai = getGeminiClient();
    const response = await ai.models.generateContent({
      model: 'gemini-3.8-flash-lite-tts',
      contents: [
        {
          role: 'user',
          parts: [
            {
              text: String(text).slice(0, 600),
              speechMetadata: {
                style: 'Warm, clear, friendly tutor',
              },
            },
          ],
        },
      ],
      config: {
        responseModalities: ['AUDIO'],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: 'Kore' },
          },
        },
      },
    });

    const base64Audio = response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;
    if (!base64Audio) {
      return res.status(500).json({ error: 'No audio returned from TTS model.' });
    }
    return res.status(200).json({ audioBase64: base64Audio, mimeType: 'audio/wav' });
  } catch (e) {
    const clean = formatCleanAiError(e);
    return res.status(clean.status).json({ error: clean.error, code: clean.code });
  }
});

app.use(express.static(path.join(__dirname)));

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Wavelord Math Lab listening on http://0.0.0.0:${PORT}`);
});
