# SQL IDE

A powerful, feature-rich SQL IDE built with JavaFX, designed to provide a comprehensive environment for database management, data science, and AI-assisted SQL development.

## 🚀 Features

### 🗄️ Database Management
- **Multi-Database Support**: Connect to and manage SQLite, MySQL, and PostgreSQL databases.
- **Visual Schema Browser**: Explore tables, views, triggers, functions, and procedures.
- **SQL Editor**: Advanced editor with autocomplete for SQL keywords and table/column names.
- **Data Manipulation**: Easily insert, update, and delete rows through a user-friendly interface.
- **Schema Evolution**: Create and modify tables, views, triggers, functions, and procedures visually.

### 🤖 AI Assistant (Aida)
- **Natural Language to SQL**: Converse with Aida to generate complex SQL queries, create tables, or even insert data using natural language.
- **Multi-Modal Interaction**: Support for text and voice input (via Microphone).
- **Web Search Integration**: Aida can perform web searches to provide more context or help with troubleshooting.
- **Database Awareness**: Aida is aware of your current schema and can perform actions directly on your database.

### 📊 Data Science & Machine Learning
- **Dataset Exploration**: Load and preview datasets with basic statistics (mean, median, std, etc.).
- **Data Cleaning**: Impute missing values using Mean, Median, Linear Regression, or KNN.
- **Outlier Detection**: Identify outliers using IQR, Z-Score, or Mean-based methods.
- **Machine Learning Models**: Train and evaluate models (Linear Regression, Random Forest, KNN, etc.) directly within the IDE.
- **Visualization**: Generate distribution charts, scatter plots, and correlation heatmaps.

### 📤 Export & Import
- **Export Formats**: Export your data to CSV, Excel (XLSX), XML, and JSON.
- **Import Formats**: Import data from CSV, Excel, XML, and JSON files.

### 📄 Reporting & Communication
- **PDF Reports**: Generate professional PDF reports from your SQL queries.
- **Email Integration**: Send data or reports directly via email with support for HTML templates and dynamic tags.

## 🛠️ Technologies Used

- **Language**: Java 24 (Preview features enabled)
- **UI Framework**: JavaFX with [AtlantaFX](https://github.com/mkpaz/atlantafx) and [JFoenix](https://github.com/sshahine/JFoenix)
- **AI**: Google Gemini (via Python bridge)
- **Machine Learning**: [Smile](https://github.com/haifengl/smile) and [DJL](https://github.com/deepjavalibrary/djl)
- **Database Drivers**: SQLite JDBC, MySQL Connector/J, PostgreSQL JDBC
- **Libraries**:
  - Apache POI (Excel)
  - Apache PDFBox (PDF)
  - JSqlParser (SQL Parsing)
  - Sphinx-4 (Voice Recognition)
  - Jakarta Mail (Email)
  - RichTextFX (Code Editor)

## ⚙️ Setup & Installation

### Prerequisites
- Java Development Kit (JDK) 24
- Maven 3.x
- Python 3.x (for AI Assistant functionality)
  - Required Python packages: `google-genai`

### Building the Project
Clone the repository and run:
```bash
./mvnw clean install
```

### Running the IDE
To launch the application:
```bash
./mvnw javafx:run
```

## 📝 Configuration
The AI Assistant requires a Gemini API key. Configure it in the `aida.py` script or through the IDE's configuration settings (if available).

## 📄 License
[Insert License Information Here]
