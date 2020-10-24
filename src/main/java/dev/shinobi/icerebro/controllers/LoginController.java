package dev.shinobi.icerebro.controllers;

import dev.shinobi.icerebro.data.model.Key;
import dev.shinobi.icerebro.data.model.LoggedInUser;
import dev.shinobi.icerebro.data.model.RegisteringUser;
import dev.shinobi.icerebro.rest.ServiceGenerator;
import dev.shinobi.icerebro.rest.iCerebroService;
import java.io.IOException;
import java.util.prefs.Preferences;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginController {
  private final iCerebroService client;
  private final Preferences prefs;

  public GridPane grid_pane;
  public TextField userNameField;
  public PasswordField passwordField;
  public Text feedback;
  public ProgressIndicator progress_bar;

  public LoginController(){
    prefs = Preferences.userRoot().node(this.getClass().getName());
    String authToken = prefs.get("authToken", null);
    client = ServiceGenerator.createService(authToken);

    if (authToken != null) {
//      grid_pane.setDisable(true);
//      progress_bar.setVisible(true);
      System.out.println("authToken: " + authToken);
//      System.out.println("feedback: " + feedback);
//      feedback.setText("Trying to login");
      // Check if user is signed in (non-null) and update UI accordingly.
      Call<LoggedInUser> call = client.getCurrentUser();
      call.enqueue(
          new Callback<LoggedInUser>() {
            @Override
            public void onResponse(
                @NotNull Call<LoggedInUser> call, @NotNull Response<LoggedInUser> response) {
              if (response.body() != null) {
                String username = response.body().getUsername();
                prefs.put("username", username);
                startTunnelActivity();
              }
//              feedback.setText("Auth Token is no longer valid, please login again");
//              grid_pane.setDisable(false);
//              progress_bar.setVisible(false);
            }

            @Override
            public void onFailure(@NotNull Call<LoggedInUser> call, @NotNull Throwable t) {
//              feedback.setText("Login Failed");
//              grid_pane.setDisable(false);
//              progress_bar.setVisible(false);
            }
          });
    }
  }

  @FXML
  private void buttonLoginPressed() throws IOException {
    String username = userNameField.getText();
    String password = passwordField.getText();
    if (username == null || username.equals("")) {
      feedback.setText("Please fill in your username");
    } else if (password == null || password.equals("")){
      feedback.setText("Please fill in your password");
    } else {
      attemptLogin(username, password);
    }
  }


  public void attemptLogin(String username, String password) throws IOException{
    grid_pane.setDisable(true);
    progress_bar.setVisible(true);
    RegisteringUser registeringUser = new RegisteringUser();
    if (username.contains("@")) {
      registeringUser.setEmail(username);
    } else {
      registeringUser.setUsername(username);
    }
    registeringUser.setPassword(password);
    Call<Key> call = client.login(registeringUser);
    call.enqueue(new Callback<Key>() {
      @Override
      public void onResponse(@NotNull Call<Key> call, @NotNull Response<Key> response) {
        if (!response.isSuccessful() && response.errorBody() != null) {
          try {
            JSONObject error = new JSONObject(response.errorBody().string());
            // TODO: modify to accept JSONError and use it to mark errors
            feedback.setText("Login Failed");
          } catch (IOException | JSONException e) {
            System.out.println("Login - onResponse\n" + e.getMessage());
          }
        } else {
          if(response.body() != null && response.body().getKey() != null) {
            String authToken = response.body().getKey();
            System.out.println("authToken: " + authToken);
            prefs.put("authToken", authToken);
            startTunnelActivity();
          } else {
            feedback.setText("Login Failed");
          }
        }
        grid_pane.setDisable(false);
        progress_bar.setVisible(false);
      }

      @Override
      public void onFailure(@NotNull Call<Key> call, @NotNull Throwable t) {
        // TODO: modify to accept onFailure throwable or simply put another string saying its that
        feedback.setText("Login Failed");
        grid_pane.setDisable(false);
        progress_bar.setVisible(false);
      }
    });
  }

  private void startTunnelActivity() {
    try {
      App.setRoot("tunnel");
    } catch (IOException e) {
      feedback.setText("Failed to start activity");
      e.printStackTrace();
    }
  }

}
