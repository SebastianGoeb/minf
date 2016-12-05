package com.sebastiangoeb.minf.driver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public class Topology {

	public List<Host> controllers;
	public List<Host> drivers;
	public List<Host> servers;
	
	public static Topology fromFile(String fileName) {
		try {
			return new Gson().fromJson(new FileReader(fileName), Topology.class);
		} catch (JsonSyntaxException e) {
			System.out.println("Invalid json file: " + fileName);
		} catch (JsonIOException e) {
			System.out.println("Error reading file: " + fileName);
		} catch (FileNotFoundException e) {
			System.out.println("No such file: " + fileName);
		}
		return null;
	}
}
