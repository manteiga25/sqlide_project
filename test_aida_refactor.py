import json
import sys
import os
from unittest.mock import patch, MagicMock

# Import the refactored aida
import src.main.java.com.example.sqlide.Assistant.service.aida as aida

def test_tool_definitions():
    assert len(aida.tools) >= 16
    assert any(t["function"]["name"] == "web_search" for t in aida.tools)
    print("Tool definitions test passed")

def test_available_functions():
    assert "web_search" in aida.available_functions
    assert "get_sql_type" in aida.available_functions
    print("Available functions test passed")

if __name__ == "__main__":
    try:
        test_tool_definitions()
        test_available_functions()
        print("All Python side tests passed locally (mocked LLM)")
    except Exception as e:
        print(f"Tests failed: {e}")
        sys.exit(1)
