from google import genai
from google.genai import types
from google.genai.types import Content, Part, GenerateContentResponse, GoogleSearch, Tool
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

def talkToGemini(prompt: str, deep: bool, search: bool, command: bool):
    global conversation_history
    global client

    model = "gemini-2.5-flash-preview-04-17" if deep else "gemini-2.0-flash"

    google_search_tool = Tool(
        google_search = GoogleSearch()
    )

    func_tools = [set_light]

    tools = None

    if search:
        tools = [google_search_tool]
    elif command:
        tools = func_tools

    config = types.GenerateContentConfig(
        tools=tools,
        system_instruction="You are a SQL Assistant. Your name is Aida.",

    )

    user_content = Content(
            role="user",
            parts=[Part(text=prompt)]
        )
    conversation_history.append(prompt)

    response = client.models.generate_content(
        model=model,
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
        prompt = input()
        jsonReciver = json.loads(prompt)
        text = talkToGemini(jsonReciver["content"], jsonReciver["deep"], jsonReciver["search"], jsonReciver["command"])
           # print(json.dumps(res))
        resposta = {
            'status': 'success',
            'message': text
        }
        print(json.dumps(resposta))

main()