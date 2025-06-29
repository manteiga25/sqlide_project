package com.example.sqlide.drivers.model.Interfaces;

import com.example.sqlide.DataForDB;

import java.util.ArrayList;
import java.util.HashMap;

public interface DatabaseFetcherInterface {

    ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, long offset, String primeKey);

    public abstract ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, String primeKey);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long offset, final boolean primeKey);

    abstract public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset, boolean PrimeKey);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Command, long limit, long offset);

    public abstract ArrayList<HashMap<String, String>> fetchRawDataMap(String Command);

    public abstract ArrayList<Double> fetchDataMap(String Command);

    public abstract ArrayList<ArrayList<String>> fetchDataBackup(String Table, ArrayList<String> Columns, long offset);

    public abstract ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long offset);

    abstract public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long limit, long offset);



}
