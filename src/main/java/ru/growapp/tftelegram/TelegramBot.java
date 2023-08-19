package ru.growapp.tftelegram;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.growapp.tftelegram.config.BotConfig;

import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramBot extends TelegramLongPollingBot {

  @Autowired
  private BotConfig botConfig;

  private String lastCommand = null;
  private final String START_COMMAND = "/start";
  private final String TRIAL_COMMAND = "/trial";

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      String messageText = update.getMessage().getText();
      long chatId = update.getMessage().getChatId();

      switch (messageText) {
        case START_COMMAND:
          lastCommand = START_COMMAND;
          startCommandReceived(
            chatId,
            update.getMessage().getChat().getFirstName()
          );
          break;
        case TRIAL_COMMAND:
          lastCommand = TRIAL_COMMAND;
          trialCommandReceived(chatId);
          break;
        default:
          if (!messageText.isEmpty() && !TRIAL_COMMAND.equals(lastCommand)) {
            defaultCommandReceived(chatId);
          } else if (TRIAL_COMMAND.equals(lastCommand)) {
            sendAdminMessage(botConfig.getAdminChatId(), messageText);
            succesCommandReceived(chatId);
          }
          lastCommand = null;
      }
    } else if (update.hasCallbackQuery()) {
      String callData = update.getCallbackQuery().getData();
      long chatId = update.getCallbackQuery().getMessage().getChatId();

      if (callData.equals("trial_message")) {
        lastCommand = TRIAL_COMMAND;
        trialCommandReceived(chatId);
      }
    }
  }

  private void startCommandReceived(Long chatId, String name) {
    String answer =
      "Здравствуйте, " +
      name +
      "! Добро пожаловать в сеть клубов Территория Фитнеса. Чем мы можем Вам помочь?";
    sendMessage(chatId, answer, true);
  }

  private void trialCommandReceived(Long chatId) {
    String answer =
      "Для записи на пробную тренировку, укажите, пожалуйста, Ваши контактные данные в обратном сообщении. Например, Имя Фамилия, телефон.";
    sendMessage(chatId, answer, false);
  }

  private void defaultCommandReceived(Long chatId) {
    String answer = "Пожалуйста, выберите доступную в меню команду.";
    sendMessage(chatId, answer, true);
  }
  private void succesCommandReceived(Long chatId) {
    String answer = "Спасибо, Ваша заявка зарегистрирована. Мы скоро свяжемся с Вами!";
    sendMessage(chatId, answer, false);
  }

  private void sendAdminMessage(Long chatId, String message) {
    String answer = "Новая запись на пробную тренировку: " + message;
    sendMessage(chatId, answer, false);
  }

  private void sendMessage(Long chatId, String textToSend, boolean showButtons) {
    SendMessage sendMessage = new SendMessage();
    sendMessage.setChatId(String.valueOf(chatId));
    sendMessage.setText(textToSend);
    try {
      if (showButtons) {
        addMenuButtons(sendMessage);
      }
      execute(sendMessage);
    } catch (TelegramApiException e) {}
  }

  private void addMenuButtons(SendMessage message) {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    List<InlineKeyboardButton> rowInline = new ArrayList<>();
    InlineKeyboardButton trialButton = new InlineKeyboardButton();
    trialButton.setText("Бесплатная пробная тренировка");
    trialButton.setCallbackData("trial_message");
    rowInline.add(trialButton);
    // Set the keyboard to the markup
    rowsInline.add(rowInline);
    // Add it to the message
    markupInline.setKeyboard(rowsInline);
    message.setReplyMarkup(markupInline);
  }

  @Override
  public String getBotUsername() {
    return botConfig.getBotName();
  }

  @Override
  public String getBotToken() {
    return botConfig.getToken();
  }
}
