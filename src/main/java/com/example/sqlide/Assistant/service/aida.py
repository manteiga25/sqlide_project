from google import genai
from google.genai import types
from google.genai.types import Content, Part, GenerateContentResponse
import inspect
import json

conversation_history = []
client = None

def set_light(brit: int):
    """Set the light state.
    Args:
        brit (int): Value between 0 and 100
    """

    sender = {
    'status': 'request',
    'function': 'set_light',
    'parameters': [str(brit)],
    'message': 'ainda changing light'
    }

    print(json.dumps(sender))

def talkToGemini(s: str):
    global conversation_history
    global client

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

    return response.text

def main():
    global client
    client = genai.Client(api_key="")
    while True:
        s = input()
        jsonReciver = json.loads(s)
        text = talkToGemini(jsonReciver["content"])
           # print(json.dumps(res))
        resposta = {
            'status': 'success',
            'message': text
        }
        print(json.dumps(resposta))

main()