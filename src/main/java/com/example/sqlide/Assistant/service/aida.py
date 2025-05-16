from genai import genai, types
import inspect
import json

conversation_history = []

def set_light(brit: int):
    """Set the light state.
    Args:
        brit (int): Value between 0 and 100
    """
    print(f"Light set to {brit}%")

def talkToGemini(s: str):
    global conversation_history

    config = types.GenerateContentConfig(
        tools=[set_light],
        system_instruction="You are a SQL Assistant. Your name is Aida.",

    )

    user_content = Content(
            role="user",
            parts=[Part(text=s)]
        )
    conversation_history.append(s)

    response = client.models.generate_content(
        model="gemini-2.0-flash",
        contents=conversation_history,
        config=config
    )

    model_response = Content(
                role="model",
                parts=[Part(text=response.text)]
            )
    conversation_history.append(model_response)

    return response.Text

def main():
    client = genai.Client(api_key="")
    while True:
        s = input()
        text = talkToGemini(s)
           # print(json.dumps(res))
        resposta = {
            'status': 'success',
            'message': text
        }
        print(json.dumps(resposta))