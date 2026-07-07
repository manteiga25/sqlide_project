import litellm
from duckduckgo_search import DDGS
import json
import sys
import os
import base64

# Configuration and state
conversation_history = []
current_settings = {}

def get_sql_type():
    """Get the type of SQL on the Schema."""
    print(json.dumps({"status": "request", "function": "type", "parameters": [], "message": "Fetching SQLType"}))
    return input_and_wait()

def show_data(query: str, table: str):
    """Show data for user from a SQL query."""
    print(json.dumps({"status": "request", "function": "Show_Data", "parameters": [query, table], "message": f"Fetching data of {table}"}))
    return input_and_wait()

def request_data(query: str, table: str):
    """Request data for user from a SQL query."""
    print(json.dumps({"status": "request", "function": "Request_Data", "parameters": [query, table], "message": f"Fetching data of {table}"}))
    return input_and_wait()

def get_columns_metadata():
    """Request metadata of table."""
    print(json.dumps({"status": "request", "function": "GetTableMeta", "parameters": [], "message": "Fetching Metadata of tables"}))
    return input_and_wait()

def current_table():
    """Fetch the current Table."""
    print(json.dumps({"status": "request", "function": "table", "parameters": [], "message": "Fetching current Table"}))
    return input_and_wait()

def send_email(body: str):
    """Send a html email."""
    print(json.dumps({"status": "request", "function": "sendEmail", "parameters": [body], "message": "Sending email"}))
    return input_and_wait()

def create_report(title: str, query: str):
    """Generate a report."""
    print(json.dumps({"status": "request", "function": "createReport", "parameters": [title, query], "message": "Creating report"}))
    return input_and_wait()

def create_table(tableName: str, meta: list, check: str = ""):
    """Create a sql table."""
    print(json.dumps({"status": "request", "function": "CreateTable", "parameters": [tableName, meta, check], "message": "Creating Table"}))
    return input_and_wait()

def create_data(table: str, data: list):
    """Create data for the table."""
    print(json.dumps({"status": "request", "function": "InsertData", "parameters": [table, data], "message": "Creating Data"}))
    return input_and_wait()

def create_view(table: str, name: str, code: str):
    """Create a view."""
    print(json.dumps({"status": "request", "function": "CreateView", "parameters": [table, name, code], "message": "Creating View"}))
    return input_and_wait()

def create_trigger(Trigger: dict):
    """Create a SQL trigger."""
    print(json.dumps({"status": "request", "function": "CreateTrigger", "parameters": [Trigger], "message": "Creating Trigger"}))
    return input_and_wait()

def create_function(Function: dict):
    """Create a SQL Function."""
    print(json.dumps({"status": "request", "function": "CreateFunction", "parameters": [Function], "message": "Creating Function"}))
    return input_and_wait()

def create_procedure(Procedure: dict):
    """Create a SQL Procedure."""
    print(json.dumps({"status": "request", "function": "CreateProcedure", "parameters": [Procedure], "message": "Creating Procedure"}))
    return input_and_wait()

def create_event(Event: dict):
    """Create a SQL Event."""
    print(json.dumps({"status": "request", "function": "CreateEvent", "parameters": [Event], "message": "Creating Event"}))
    return input_and_wait()

def create_graphic(table: str, name: str, nameX: str, nameY: str, label: list):
    """Create a Graphic."""
    print(json.dumps({"status": "request", "function": "CreateGraphic", "parameters": [table, name, nameX, nameY, label], "message": "Creating Graphic"}))
    return input_and_wait()

def web_search(query: str):
    """Search the web for information using DuckDuckGo."""
    try:
        with DDGS() as ddgs:
            results = [r for r in ddgs.text(query, max_results=5)]
            return json.dumps(results)
    except Exception as e:
        return f"Error searching the web: {str(e)}"

def input_and_wait():
    line = sys.stdin.readline()
    if not line:
        return ""
    try:
        resp = json.loads(line)
        if resp.get("type") == "response":
            return resp.get("content", "")
    except:
        pass
    return line.strip()

# OpenAI-style tool definitions
tools = [
    {"type": "function", "function": {"name": "get_sql_type", "description": "Get the type of SQL on the Schema (MySQL, SQLite, PostgreSQL, etc.)"}},
    {"type": "function", "function": {"name": "show_data", "description": "Show data for user from a SQL query.", "parameters": {"type": "object", "properties": {"query": {"type": "string"}, "table": {"type": "string"}}, "required": ["query", "table"]}}},
    {"type": "function", "function": {"name": "request_data", "description": "Request data for user from a SQL query.", "parameters": {"type": "object", "properties": {"query": {"type": "string"}, "table": {"type": "string"}}, "required": ["query", "table"]}}},
    {"type": "function", "function": {"name": "get_columns_metadata", "description": "Request metadata of table to process metadata of columns."}},
    {"type": "function", "function": {"name": "current_table", "description": "Fetch the current Table on the user is."}},
    {"type": "function", "function": {"name": "send_email", "description": "Send a html email.", "parameters": {"type": "object", "properties": {"body": {"type": "string"}}, "required": ["body"]}}},
    {"type": "function", "function": {"name": "create_report", "description": "Generate a report.", "parameters": {"type": "object", "properties": {"title": {"type": "string"}, "query": {"type": "string"}}, "required": ["title", "query"]}}},
    {"type": "function", "function": {"name": "create_table", "description": "Create a sql table.", "parameters": {"type": "object", "properties": {"tableName": {"type": "string"}, "meta": {"type": "array", "items": {"type": "object"}}, "check": {"type": "string"}}, "required": ["tableName", "meta"]}}},
    {"type": "function", "function": {"name": "create_data", "description": "Create data for the table.", "parameters": {"type": "object", "properties": {"table": {"type": "string"}, "data": {"type": "array", "items": {"type": "object"}}}, "required": ["table", "data"]}}},
    {"type": "function", "function": {"name": "create_view", "description": "Create a view for the table.", "parameters": {"type": "object", "properties": {"table": {"type": "string"}, "name": {"type": "string"}, "code": {"type": "string"}}, "required": ["table", "name", "code"]}}},
    {"type": "function", "function": {"name": "create_trigger", "description": "Create a SQL trigger.", "parameters": {"type": "object", "properties": {"Trigger": {"type": "object"}}, "required": ["Trigger"]}}},
    {"type": "function", "function": {"name": "create_function", "description": "Create a SQL Function.", "parameters": {"type": "object", "properties": {"Function": {"type": "object"}}, "required": ["Function"]}}},
    {"type": "function", "function": {"name": "create_procedure", "description": "Create a SQL Procedure.", "parameters": {"type": "object", "properties": {"Procedure": {"type": "object"}}, "required": ["Procedure"]}}},
    {"type": "function", "function": {"name": "create_event", "description": "Create a SQL Event.", "parameters": {"type": "object", "properties": {"Event": {"type": "object"}}, "required": ["Event"]}}},
    {"type": "function", "function": {"name": "create_graphic", "description": "Create a Graphic.", "parameters": {"type": "object", "properties": {"table": {"type": "string"}, "name": {"type": "string"}, "nameX": {"type": "string"}, "nameY": {"type": "string"}, "label": {"type": "array", "items": {"type": "object"}}}, "required": ["table", "name", "nameX", "nameY", "label"]}}},
    {"type": "function", "function": {"name": "web_search", "description": "Search the web for information using DuckDuckGo.", "parameters": {"type": "object", "properties": {"query": {"type": "string"}}, "required": ["query"]}}}
]

available_functions = {
    "get_sql_type": get_sql_type,
    "show_data": show_data,
    "request_data": request_data,
    "get_columns_metadata": get_columns_metadata,
    "current_table": current_table,
    "send_email": send_email,
    "create_report": create_report,
    "create_table": create_table,
    "create_data": create_data,
    "create_view": create_view,
    "create_trigger": create_trigger,
    "create_function": create_function,
    "create_procedure": create_procedure,
    "create_event": create_event,
    "create_graphic": create_graphic,
    "web_search": web_search
}

def chat_with_llm(prompt, image_base64=None, search_enabled=False, command_enabled=False, deep_mode=False, settings={}):
    global conversation_history

    provider = settings.get("provider", "Gemini")
    api_key = settings.get("apiKey", "")
    model = settings.get("modelName", "gemini-1.5-flash")
    base_url = settings.get("baseUrl", "")

    # Set up LiteLLM
    litellm_model = model
    if provider == "OpenAI":
        litellm_model = f"openai/{model}"
        os.environ["OPENAI_API_KEY"] = api_key
    elif provider == "Gemini":
        litellm_model = f"gemini/{model}"
        os.environ["GEMINI_API_KEY"] = api_key
    elif provider == "Claude":
        litellm_model = f"anthropic/{model}"
        os.environ["ANTHROPIC_API_KEY"] = api_key
    elif provider == "Ollama":
        litellm_model = f"ollama/{model}"
        if base_url: os.environ["OLLAMA_API_BASE"] = base_url
    elif provider == "LM Studio":
        litellm_model = f"openai/{model}" # LM Studio uses OpenAI compatible API
        os.environ["OPENAI_API_KEY"] = "not-needed"
        if base_url: litellm.api_base = base_url

    content = [{"type": "text", "text": prompt}]
    if image_base64:
        content.append({
            "type": "image_url",
            "image_url": {"url": f"data:image/jpeg;base64,{image_base64}"}
        })

    conversation_history.append({"role": "user", "content": content})

    active_tools = []
    if command_enabled:
        active_tools = [t for t in tools if t["function"]["name"] != "web_search"]
    if search_enabled:
        active_tools.append([t for t in tools if t["function"]["name"] == "web_search"][0])

    try:
        response = litellm.completion(
            model=litellm_model,
            messages=[{"role": "system", "content": "You are a SQL Assistant. Your name is Aida."}] + conversation_history,
            tools=active_tools if active_tools else None,
            tool_choice="auto" if active_tools else None
        )

        response_message = response.choices[0].message

        # Handle tool calls
        if response_message.get("tool_calls"):
            conversation_history.append(response_message)
            for tool_call in response_message["tool_calls"]:
                function_name = tool_call.function.name
                function_to_call = available_functions[function_name]
                function_args = json.loads(tool_call.function.arguments)

                function_response = function_to_call(**function_args)

                conversation_history.append({
                    "tool_call_id": tool_call.id,
                    "role": "tool",
                    "name": function_name,
                    "content": str(function_response),
                })

            # Second call to get final answer
            response = litellm.completion(
                model=litellm_model,
                messages=[{"role": "system", "content": "You are a SQL Assistant. Your name is Aida."}] + conversation_history
            )
            response_message = response.choices[0].message

        final_content = response_message.get("content", "")
        # Check for reasoning/thinking mode (e.g. from DeepSeek or similar)
        if hasattr(response_message, 'reasoning_content') and response_message.reasoning_content:
            final_content = f"<think>\n{response_message.reasoning_content}\n</think>\n{final_content}"

        conversation_history.append({"role": "assistant", "content": final_content})
        return final_content

    except Exception as e:
        return f"Error: {str(e)}"

def inflate(context):
    global conversation_history
    conversation_history = []
    for obj in context:
        if "User" in obj:
            conversation_history.append({"role": "user", "content": obj["User"]})
        elif "Assistant" in obj:
            conversation_history.append({"role": "assistant", "content": obj["Assistant"]})

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            data = json.loads(line)

            if data["type"] == "message":
                text = chat_with_llm(
                    data["content"],
                    data.get("image"),
                    data.get("search"),
                    data.get("command"),
                    data.get("deep"),
                    data.get("settings", {})
                )
                print(json.dumps({"status": "success", "message": text}))
            elif data["type"] == False: # Context inflation
                inflate(data["content"])
        except Exception as e:
            print(json.dumps({"status": "error", "message": str(e)}), file=sys.stderr)

if __name__ == "__main__":
    main()
