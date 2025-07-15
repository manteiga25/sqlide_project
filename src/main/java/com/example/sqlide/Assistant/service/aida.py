from google import genai
from google.genai import types
from google.genai.types import Content, Part, GenerateContentResponse, GoogleSearch, Tool
import json
from sys import stderr

conversation_history = []
client = None
response = []

def getSQLType():
    """Get the type of SQL on the Schema, it's necessary to generate functions/triggers/events, etc..., to generate a correct code.
        Args:

        Return (str): The name of SQLType.
        """
    global response
    sender = {
            'status': 'request',
            'function': 'type',
            'parameters': [],
            'message': 'Fetching SQLType'
            }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def currentTable():
    """Fetch the current Table on the user is.
    Args:

    Return (str): the table name of the user if value is "" is invalid.
    """
    global response
    sender = {
        'status': 'request',
        'function': 'table',
        'parameters': [],
        'message': 'Fetching current Table'
        }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def ShowData(query: str, table: str):
    """Show data for user from a SQL query, If table is not mentioned use currentTable() to fetch current Table.
    Args:
        query (str): Generate the query to execute fetch. You cannot use LIMIT and OFFSET on query
        table (str): The table to execute query.
    Return (bool): True success, False error
    """
    global response
    sender = {
    'status': 'request',
    'function': 'Show_Data',
    'parameters': [query, table],
    'message': f'Fetching data of {table}'
    }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def RequestData(query: str, table: str):
    """Request data for user from a SQL query, If table is not mentioned use currentTable() to fetch current Table.
    Args:
        query (str): Generate the query to execute fetch.
        table (str): The table to execute query.
    Return (list(dict[str, str])): the list of data
    """
    global response
    sender = {
    'status': 'request',
    'function': 'Request_Data',
    'parameters': [query, table],
    'message': f'Fetching data of {table}'
    }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def GetColumnsMetadata():
    """Request metadata of table to process metadata of columns.
    Args:

    Return (list[dict[str, str]]: the list of metadata
    """
    global response
    sender = {
    'status': 'request',
    'function': 'GetTableMeta',
    'parameters': [],
    'message': 'Fetching Metadata of tables'
    }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def sendEmail(body: str, query: list[str]):
    """Generate and send a html email body ex: <html dir="ltr"><head></head><body contenteditable="true"><span style="font-family: &quot;&quot;;">Olá&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=user:Name/&gt;</span><span style="font-family: &quot;&quot;;">, parabéns foi selecionado como candidato para o prémio por ter completado os seus&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=user:age/&gt; de idade, vá para a lojá maos proxima e use o seguinte código&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=product:id/&gt; para receber o prémio, obrigado.&nbsp;</span><span style="font-family: &quot;&quot;;">&lt;DataSrc=worker:Name/&gt;.</span></body></html>
        use GetColumnsMetadata function to get information of tables to insert data tag to email ex: <DataSrc='table':'column'/>.
        Args:
            body (str): The html content of email.

        Return None:
    """
    global response
    sender = {
        'status': 'request',
        'function': 'sendEmail',
        'parameters': [body],
        'message': ''
        }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def createReport(title: str, query: str):
    """Generate a report, use GetColumnsMetadata and currentTable function to get information of tables to to generate query for report.
            Args:
                title (str): The title of report.
                query (str): The SQL Query for report, generate.

            Return (bool): true success, false error.
        """
    global response
    sender = {
            'status': 'request',
            'function': 'createReport',
            'parameters': [title, query],
            'message': ''
            }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def createTable(tableName: str, meta: list[dict[str, str]]):
    """Create a sql table, if not specified from user try to generate.
        Args:
            tableName (str): The name of table.
            meta (list[dict[str, str]]): the little metadata (columns) of table (Name: 'column_name', Type: 'type of column', Key: 'Primary key = 'PRIMARY KEY', foreign key = 'FOREIGN KEY', No key = 'NO KEY', NotNull: 'true' 'false' ).

        Return (bool): true success, false error.
    """
    global response
    sender = {
            'status': 'request',
            'function': 'CreateTable',
            'parameters': [tableName, meta],
            'message': ''
            }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def createData(table: str, data: list[dict[str, str]]):
    """Create a data for the table's.
        Args:
            table: The table to insert data, if not mentioned by the user use currentTable() to fetch current Table.
            data: The list of dictionary of data, the keys of dict is the columns Name and the values is String (The application convert to real type of column).

        Return (str): empty Success, Error or SQLException Error.
    """
    global response
    sender = {
                'status': 'request',
                'function': 'InsertData',
                'parameters': [table, data],
                'message': ''
                }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def createTrigger(Trigger: dict[str, str]):
    """Create a SQL trigger's for the Schema.
         Args:
            Trigger: dictionary, key are the name of trigger and value is the code of trigger.

         Return (str): true success, false or SQLException error.
    """
    global response
    sender = {
                'status': 'request',
                'function': 'CreateTrigger',
                'parameters': [Trigger],
                'message': ''
                }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def createEvent(Event: dict[str, str]):
    """Create a SQL Event's for the Schema.
             Args:
                Event: dictionary, key are the name of event and value is the code of event.

             Return (str): true success, false or SQLException error.
        """
    global response
    sender = {
                'status': 'request',
                'function': 'CreateEvent',
                'parameters': [Event],
                'message': ''
                }

    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def createGraphic(table: str, name: str, nameX: str, nameY: str, label: list[dict[str, str]]):
    """Create a Graphic (use GetColumnsMetadata for column names).
                 Args:
                    table (str): The name of table to fetch.
                    name (str): The name of graphic.
                    nameX (str): The name of X for graphic.
                    nameY (str): The name of Y for graphic.
                    label (list[dict[str, str]): The list of labels (label have the attributes: ("func", "SUM", "AVG", "COUNT", "MIN", "MAX"), ("column", the column to fetch), ("group", the group of label can be the same with other label for line graphic example (generate the group name)), ("category", The unique category name of label), ("query", The query of fetch, use the same function to this query))

                 Return (str): true success, false or SQLException error.
            """
    global response
    sender = {
                    'status': 'request',
                    'function': 'CreateGraphic',
                    'parameters': [table, name, nameX, nameY, label],
                    'message': ''
                    }
    print(json.dumps(sender))

    ret = input()

    response.append(ret)

    return ret

def talkToGemini(prompt: str, deep: bool, search: bool, command: bool):
    global conversation_history
    global client
    global response

    model = "gemini-2.5-pro" if deep else "gemini-2.5-flash"

    google_search_tool = Tool(
        google_search = GoogleSearch()
    )

    func_tools = [getSQLType, ShowData, RequestData, GetColumnsMetadata, sendEmail, createTable, currentTable, createData, createReport, createTrigger, createEvent, createGraphic]

    tools = None

    if search:
        tools = [google_search_tool]
    elif command:
        tools = func_tools

    config = types.GenerateContentConfig(
        tools=tools,
        system_instruction="You are a SQL Assistant. Your name is Aida."
    )

    user_content = Content(
            role="user",
            parts=[Part(text=prompt)]
        )
    conversation_history.append(user_content)

   # while True:

    response = client.models.generate_content(
            model=model,
            contents=conversation_history,
            config=config
        )



    #    if not command or not response.candidates[0].content.parts:
     #       break

      #  for part in response.candidates[0].content.parts:
       #   if part.thought_signature:
        #      conversation_history.append(response.candidates[0].content)
         #     conversation_history.append(Content(
          #                                                        role="model",
           #                                                       parts=[Part(text=part.thought_signature)]
            #                                                  ))

    model_response = Content(
                        role="model",
                        parts=[Part(text=response.candidates[0].content.parts[0].text)]
                    )
    conversation_history.append(model_response)

    return response.candidates[0].content.parts[0].text

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