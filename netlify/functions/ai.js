// Netlify Function - Wavelord MATH LAB - UNLOCKED AI
// Env var: GROQ_API_KEY
// Model: openai/gpt-oss-20b

exports.handler = async (event) => {
  const headers = {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Content-Type",
    "Content-Type": "application/json",
  };
  if (event.httpMethod === "OPTIONS") {
    return { statusCode: 200, headers, body: "" };
  }
  try {
    const { persona, question, topic } = JSON.parse(event.body || "{}");
    if (!question) {
      return { statusCode: 400, headers, body: JSON.stringify({ error: "No question" }) };
    }
    const isBuddy = persona === "buddy";
    const systemPrompt = isBuddy
      ? `You are ∑ Math Buddy, a warm friendly best-friend AI in the Wavelord Math Lab app. You can answer ANYTHING - maths, science, homework, life, jokes, stories, coding, history, anything the user asks. Be encouraging, funny, kind. Keep replies short, 2-4 sentences, easy to read on a phone. If it's maths, use real life examples like money, pizza, shop. User is on topic: ${topic || "General"}.`
      : `You are the Teacher in Wavelord Math Lab. You can answer ANYTHING - maths, science, school, life, general knowledge. Explain clearly with WORKING and step-by-step when it helps. Give one real life example and one try-this when it fits. Keep it clear, 3-5 short paragraphs max. User is on topic: ${topic || "General"}.`;

    const res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${process.env.GROQ_API_KEY}`,
      },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: question },
        ],
        temperature: 0.7,
        max_tokens: 800,
      }),
    });
    const data = await res.json();
    const answer = data?.choices?.[0]?.message?.content || "I'm quiet right now, try again.";
    return { statusCode: 200, headers, body: JSON.stringify({ answer }) };
  } catch (e) {
    return { statusCode: 500, headers, body: JSON.stringify({ error: "AI couldn't reach Netlify: " + e.message }) };
  }
};
