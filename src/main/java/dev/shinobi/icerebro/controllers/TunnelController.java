package dev.shinobi.icerebro.controllers;

import com.jcraft.jsch.JSchException;

import dev.shinobi.icerebro.data.model.ProxyPort;
import dev.shinobi.icerebro.data.model.PubKey;
import dev.shinobi.icerebro.rest.ServiceGenerator;
import dev.shinobi.icerebro.rest.iCerebroService;
import dev.shinobi.icerebro.ssh.JSCHTunnel;
import java.net.BindException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Objects;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TunnelController {
  public BorderPane border_pane;
  public Label title;
  public ScrollPane monitor_scroll;
  public Text monitor_text;
  public Button start_stop_button;
  public ProgressIndicator progress_bar;

  private iCerebroService client;
  private Preferences prefs;

  private Monitor monitor;

  private String user = "caerisse";
  private int ssh_port = 7101;
  private int local_port = 6184;
  private String host;
  private int remote_port;
  private JSCHTunnel tunnel;

  private ExecutorService executor;
  private boolean running = false;
  private boolean stopping = false;

  public TunnelController() {
    monitor = new Monitor();
    prefs = Preferences.userRoot().node(this.getClass().getName());
    String authToken = prefs.get("authToken", null);
    client = ServiceGenerator.createService(authToken);
  }

  @FXML
  private void start_stop() {
    if (running && !stopping) {
      monitor.writeToMonitor("Stopping");
      start_stop_button.setDisable(true);
      stop();
    } else if (!running) {
      monitor.writeToMonitor("Starting");
      start_stop_button.setDisable(true);
      start();
    }
  };

  public class Monitor {
    public void writeToMonitor(String line){
      System.out.println("writeToMonitor: " + line);
      synchronized (this) {
        Platform.runLater(() -> {
//            // Todo append and rotate text as in terminal output
          String new_text = monitor_text.getText() + "\n" + line;
          monitor_text.setText(new_text);
          monitor_scroll.setVvalue(1.0);
        });
      }
    }

    public void writeTitle(String new_title){
      System.out.println("writeTitle: " + new_title);
      synchronized (this) {
        Platform.runLater(() -> title.setText(new_title));
      }
    }

    public void writeButton(String text){
      System.out.println("writeButton: " + text);
      synchronized (this) {
        Platform.runLater(() -> {
            start_stop_button.setText(text);
            start_stop_button.textProperty().setValue(text);
        });
      }
    }
//
    public void setProgressBarVisibility(boolean visibility) {
      System.out.println("setProgressBarVisibility: " + visibility);
      synchronized (this) {
        Platform.runLater(() -> {
            border_pane.setDisable(visibility);
            progress_bar.setVisible(visibility);
        });
      }
    }
  }

  private void start() {
    running = true;
    monitor.setProgressBarVisibility(true);
    monitor.writeTitle("Starting");
    monitor.writeToMonitor("Getting or generating public key");
    tunnel = new JSCHTunnel();
    tunnel.setRootDir("iCerebro");
    File[] keys = tunnel.generateAuthKeys();
    String pubKey = "";
    try (BufferedReader br = new BufferedReader(new FileReader(keys[1].getAbsolutePath()))) {
      StringBuilder contentBuilder = new StringBuilder();
      String sCurrentLine;
      while ((sCurrentLine = br.readLine()) != null) {
        contentBuilder.append(sCurrentLine).append("\n");
      }
      pubKey = contentBuilder.toString();
      savePubKey(pubKey);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void stop() {
    monitor.setProgressBarVisibility(true);
    running = false;
    stopping = true;
  }

  public void savePubKey(String pub_key_string) {
    monitor.writeToMonitor("Sending public key to server");
    monitor.writeToMonitor("pub_key: " + pub_key_string);

    PubKey pubKey = new PubKey();
    pubKey.setKey(pub_key_string);

    Call<PubKey> call = client.savePubKey(pubKey);
    call.enqueue(new Callback<PubKey>() {
      @Override
      public void onResponse(@NotNull Call<PubKey> call, @NotNull Response<PubKey> response) {
        if (!response.isSuccessful() && response.errorBody() != null) {
          monitor.writeToMonitor("ERROR sending public key to server");
          try {
            JSONObject error = new JSONObject(response.errorBody().string());
            // TODO: handle error
          } catch (IOException | JSONException e) {
            System.out.println("savePubKey - onResponse: " + e.getCause());
            e.printStackTrace();
          }
        } else {
          monitor.writeToMonitor("Sending public key to server -> OK");
          getProxyPort();
        }
      }

      @Override
      public void onFailure(@NotNull Call<PubKey> call, @NotNull Throwable t) {
        System.out.println("savePubKey - onFailure: " + t.getMessage());
        monitor.writeToMonitor("ERROR sending public key to server");
      }
    });
  }

  public void getProxyPort() {
    monitor.writeToMonitor("Getting proxy port");
    Call<ProxyPort> call = client.getProxyPort();
    call.enqueue(new Callback<ProxyPort>() {
      @Override
      public void onResponse(@NotNull Call<ProxyPort> call, @NotNull Response<ProxyPort> response) {
        if (!response.isSuccessful() && response.errorBody() != null) {
          monitor.writeToMonitor("ERROR getting proxy port");
          try {
            JSONObject error = new JSONObject(response.errorBody().string());
            // TODO: handle error
          } catch (IOException | JSONException e) {
            System.out.println("getProxyPort - onResponse: " +  e.getMessage());
          }
        } else {
          if (response.body() != null) {
            monitor.writeToMonitor("Getting proxy port -> OK");
            remote_port = response.body().getPort();
            host = response.body().getHost();
            executor = Executors.newSingleThreadExecutor();
            executor.execute(sshConnection);
          } else {
            System.out.println("getProxyPort - onResponse: body is null");
            // TODO: handle error
          }
        }
      }

      @Override
      public void onFailure(@NotNull Call<ProxyPort> call, @NotNull Throwable t) {
        System.out.println("getProxyPort - onFailure: " + t.getMessage());
      }
    });
  }

  Runnable sshConnection = () -> {
    try {
      monitor.writeToMonitor("Starting SSH tunnel");
      tunnel.connect(host, ssh_port, user, monitor);
      tunnel.startReverseDynamicPortForwarding(remote_port, host, local_port, monitor);
      monitor.writeButton("Stop");
      start_stop_button.setDisable(false);
      monitor.setProgressBarVisibility(false);
      monitor.writeToMonitor("SSH tunnel ready!");
      while (running) {
//        monitor.setProgressBarVisibility(false);
//        System.out.println("running");
        Thread.sleep(10000);
      }
    } catch (JSchException e) {
      // TODO, if error is port is occupied inform server and get a new port
      e.printStackTrace();
    } catch (InterruptedException e) {
      System.out.println("sshConnection thread interrupted");
    } finally {
      if (!stopping){
        monitor.writeToMonitor(
            "Some error was encountered in the sshConnection thread, " +
            "please try again, if this persist please contact an admin"
        );
      }
      if (tunnel != null) {
        tunnel.stop();
      }
      stopping = false;
      monitor.writeTitle("Inactive");
      monitor.writeToMonitor("Stopped");
      monitor.writeButton("Start");
      start_stop_button.setDisable(false);
      monitor.setProgressBarVisibility(false);
    }
  };

}