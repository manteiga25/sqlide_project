from google import genai
from google.genai import types
from google.genai.types import Content, Part, GenerateContentResponse, GoogleSearch, Tool
import json
from sys import stderr

conversation_history = []
client = None

def ShowData(query: str, table: str):
    """Show data for user from a SQL query.
    Args:
        query (str): Generate the query to execute fetch.
        table (str): The table to execute query.
    Return (bool): True success, False error
    """

    sender = {
    'status': 'request',
    'function': 'Show_Data',
    'parameters': [query, table],
    'message': f'Fetching data of {table}'
    }

    print(json.dumps(sender))

    return input()

def RequestData(query: str, table: str):
    """Show data for user from a SQL query.
    Args:
        query (str): Generate the query to execute fetch.
        table (str): The table to execute query.
    Return (list(dict[str, str])): the list of data
    """

    sender = {
    'status': 'request',
    'function': 'Request_Data',
    'parameters': [query, table],
    'message': f'Fetching data of {table}'
    }

    print(json.dumps(sender))

    return input()

def GetColumnsMetadata():
    """Request metadata of table to process metadata of columns.
    Args:

    Return (list[dict[str, str]]: the list of metadata
    """

    sender = {
    'status': 'request',
    'function': 'GetTableMeta',
    'parameters': [],
    'message': 'Fetching Metadata of tables'
    }

    print(json.dumps(sender))

    return input()

def sendEmail(body: str):
    """Generate and send a html email body ex: <html dir="ltr"><head></head><body contenteditable="true"><span style="font-family: &quot;&quot;;">Olá&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=user:Name/&gt;</span><span style="font-family: &quot;&quot;;">, parabéns foi selecionado como candidato para o prémio por ter completado os seus&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=user:age/&gt; de idade, vá para a lojá maos proxima e use o seguinte código&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=product:id/&gt; para receber o prémio, obrigado.&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=worker:Name/&gt;.</span></body></html>
        use GetColumnsMetadata function to get information odf tables to insert data tag to email ex: <DataSrc='table':'column'/>.
        Args:
            body: (str): The html content of email.

        Return None:
    """

    sender = {
        'status': 'request',
        'function': 'sendEmail',
        'parameters': [body],
        'message': ''
        }

    print(json.dumps(sender))

    return input()

def createTable(tableName: str, meta: list[dict[str, str]]):
    """Create a sql table, if not specified from user try to generate.
        Args:
            tableName (str): The name of table.
            meta (list[dict[str, str]]): the little metadata (columns) of table (Name: 'column_name', Type: 'type of column', Key: 'Primary key = 'PRIMARY KEY', foreign key = 'FOREIGN KEY', No key = 'NO KEY', NotNull: 'true' 'false' ).

        Return (bool): true success, false error.
    """

    sender = {
            'status': 'request',
            'function': 'CreateTable',
            'parameters': [tableName, meta],
            'message': ''
            }

    print(json.dumps(sender))

    return input()

def talkToGemini(prompt: str, deep: bool, search: bool, command: bool):
    global conversation_history
    global client

    model = "gemini-2.5-flash-preview-04-17" if deep else "gemini-2.0-flash"

    google_search_tool = Tool(
        google_search = GoogleSearch()
    )

    func_tools = [ShowData, RequestData, GetColumnsMetadata, sendEmail, createTable]

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
    client = genai.Client(api_key="AIzaSyA2OOhuUGZiJe3NQTjQLMXqNur3yLcOf0g")
    while True:
        prompt = input()
        jsonReciver = json.loads(prompt)
        try:
            text = talkToGemini(jsonReciver["content"], jsonReciver["deep"], jsonReciver["search"], jsonReciver["command"])
               # print(json.dumps(res))
            resposta = {
                'status': 'success',
                'message': text
            }
            print(json.dumps(resposta))
        except Exception as e:
            resposta = {
                 'status': 'success',
                 'message': f"Error to generate response.\n{e}"
            }
            print(json.dumps(resposta), file=stderr)

main()