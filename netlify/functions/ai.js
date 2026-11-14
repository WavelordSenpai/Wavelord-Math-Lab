// Netlify Function - Wavelord MATH LAB V42
// Needs env var in Netlify: GROQ_API_KEY
// Model: openai/gpt-oss-20b - NOT llama

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
      ? `You are ∑ Math Buddy, a warm friendly buddy in the Wavelord Math Lab app. You explain maths simply, with real life examples, like money, pizza, shop, temperature. Be encouraging, funny, kind, like a best friend. Keep replies short, 2-4 sentences, easy to read on a phone. User is on topic: ${topic || "Math Lab"}. Never talk about Library, Wave Crystal, or surfing. Only maths.`
      : `You are the Math Teacher in Wavelord Math Lab. Explain clearly with WORKING, step-by-step. Use topics from the app: Addition, Subtraction, Multiplication, Division, Negatives, Unit Circle, Euler e, Sine Wave, Pythagoras, Formulas. Keep it clear, 3-5 short paragraphs max. Give one real life example and one try-this. No Library, no Crystal, no surfing. User is on topic: ${topic || "Math Lab"}.`;

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
        max_tokens: 500,
      }),
    });

    const data = await res.json();
    const answer = data?.choices?.[0]?.message?.content || "Math Buddy is quiet right now, try again.";
    return { statusCode: 200, headers, body: JSON.stringify({ answer }) };
  } catch (e) {
    return { statusCode: 500, headers, body: JSON.stringify({ error: "Math Buddy couldn't reach Netlify: " + e.message }) };
  }
};
