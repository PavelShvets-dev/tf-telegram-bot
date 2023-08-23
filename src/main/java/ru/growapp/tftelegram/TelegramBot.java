package ru.growapp.tftelegram;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.core.config.builder.api.LoggableComponentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.growapp.tftelegram.config.BotConfig;
import ru.growapp.tftelegram.dic.Constants;
import ru.growapp.tftelegram.model.Button;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class TelegramBot extends TelegramLongPollingBot {

  Logger logger = LoggerFactory.getLogger(TelegramBot.class);

  @Autowired
  private BotConfig botConfig;

  private final Constants constants = new Constants();

  private HashMap<Long, String> lastCommand = new HashMap<>();
  private HashMap<Long, Integer> lastMessageId = new HashMap<>();
  private HashMap<Long, String> selectedStep = new HashMap<>();
  private HashMap<Long, String> selectedInstructor = new HashMap<>();
  private HashMap<Long, String> selectedWorkout = new HashMap<>();
  private HashMap<Long, String> contact = new HashMap<>();
  private final String START_COMMAND = "/start";
  private final String TRIAL_COMMAND = "/trial";
  private final String FEEDBACK_COMMAND = "/feedback";

  private final String downArrow = "\u2B07";
  private final String leftArrow = "\u2190";

  @Override
  public void onUpdateReceived(Update update) {
    if (update.hasMessage() && update.getMessage().hasText()) {
      String messageText = update.getMessage().getText();
      long chatId = update.getMessage().getChatId();
      Long userId = update.getMessage().getFrom().getId();

      switch (messageText) {
        case START_COMMAND:
          lastCommand.put(userId, START_COMMAND);
          selectedInstructor.remove(userId);
          selectedWorkout.remove(userId);
          contact.remove(userId);
          startCommandReceived(
            chatId,
            update.getMessage().getChat().getFirstName(),
            update.getMessage().getChat().getUserName(),
                  userId
          );
          break;
        case TRIAL_COMMAND:
          lastCommand.put(userId, TRIAL_COMMAND);
          selectedInstructor.remove(userId);
          selectedWorkout.remove(userId);
          contact.remove(userId);
          trialCommandReceived(chatId, userId);
          break;
        case FEEDBACK_COMMAND:
          lastCommand.put(userId, FEEDBACK_COMMAND);
          selectedInstructor.remove(userId);
          selectedWorkout.remove(userId);
          contact.remove(userId);
          feedbackCommandReceived(chatId, userId);
          break;
        default:
          if (messageText.trim().isEmpty() && !TRIAL_COMMAND.equals(lastCommand.get(userId))) {
            defaultCommandReceived(chatId, userId);
          } else if (TRIAL_COMMAND.equals(lastCommand.get(userId))) {
            if (selectedInstructor == null) {
              instructorCommandReceived(chatId, userId);
            } else if (selectedWorkout == null) {
              workoutCommandReceived(chatId, userId);
            } else {
              sendAdminMessage(botConfig.getAdminChatId(), messageText, userId);
              succesCommandReceived(chatId, userId);
            }
          } else if (FEEDBACK_COMMAND.equals(lastCommand.get(userId))) {
            sendFeedback(userId, update.getMessage().getFrom().getUserName(), messageText);
            succesFeedbackReceived(chatId, userId);
          } else {
            startCommandReceived(
                    chatId,
                    update.getMessage().getChat().getFirstName(),
                    update.getMessage().getChat().getUserName(),
                    userId
            );
          }
          lastCommand.remove(userId);
      }
    } else if (update.hasCallbackQuery()) {
      String callData = update.getCallbackQuery().getData();
      long chatId = update.getCallbackQuery().getMessage().getChatId();
      Long userId = update.getCallbackQuery().getFrom().getId();

      if (callData.equals("trial_message")) {
        lastCommand.put(userId, TRIAL_COMMAND);
        selectedInstructor.remove(userId);
        selectedWorkout.remove(userId);
        selectedStep.remove(userId);
        contact.remove(userId);
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("select_class_message") || callData.startsWith("select_instructor_message") || callData.startsWith("skip_instructor_message")) {
        selectedStep.put(userId, callData);
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("instructor_message")) {
        selectedInstructor.put(userId, callData.replace("instructor_message", ""));
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("workout_message")) {
        selectedWorkout.put(userId, constants.getWorkouts().get(Integer.parseInt(callData.replace("workout_message", ""))));
        trialCommandReceived(chatId, userId);
      } else if (callData.startsWith("feedback_message")) {
        lastCommand.put(userId, FEEDBACK_COMMAND);
        feedbackCommandReceived(chatId, userId);
      } else if (callData.equals("reset_trial_message")) {
        selectedInstructor.remove(userId);
        selectedWorkout.remove(userId);

        String lastStep = selectedStep.get(userId);
        selectedStep.remove(userId);

        if (lastMessageId.get(userId) != null) {
          DeleteMessage deleteMessage = new DeleteMessage();
          deleteMessage.setChatId(chatId);
          deleteMessage.setMessageId(lastMessageId.get(userId));
          try {
            execute(deleteMessage);
          } catch (TelegramApiException e) {
            logger.error("Failed to delete last message", e);
          }
        }

        if (lastStep == null) {
          defaultCommandReceived(chatId, userId);
        } else {
          trialCommandReceived(chatId, userId);
        }
      }
    } else if (update.hasMessage() && update.getMessage().getContact() != null) {
      long chatId = update.getMessage().getChatId();
      Long userId = update.getMessage().getFrom().getId();

      contact.put(userId, update.getMessage().getContact().getPhoneNumber() + ", "
              + update.getMessage().getContact().getFirstName() + " "
              + update.getMessage().getContact().getLastName());

      if (selectedInstructor.get(userId) == null || selectedWorkout.get(userId) == null) {
        trialCommandReceived(chatId, userId);
      } else {
        sendAdminMessage(botConfig.getAdminChatId(), contact.get(userId), userId);
        succesCommandReceived(chatId, userId);
      }
    }
  }

  private void startCommandReceived(Long chatId, String name, String userName, Long userId) {
    String answer =
      "Здравствуйте, " +
      name +
      "! Добро пожаловать в сеть клубов Территория Фитнеса. Чем мы можем Вам помочь?";
    sendMessage(chatId, answer, createStartButtons(), userId);
    sendMessage(botConfig.getAdminChatId(), "Пользователь @" + userName + " начал использовать бот.", userId);
  }

  private void trialCommandReceived(Long chatId, Long userId) {
    if (selectedStep.get(userId) == null) {
      trialStepCommandReceived(chatId, userId);
    } else {
      if ("select_instructor_message".equals(selectedStep.get(userId)) && selectedInstructor.get(userId) == null) {
        instructorCommandReceived(chatId, userId);
      } else if ("select_class_message".equals(selectedStep.get(userId))) {
        if (selectedWorkout.get(userId) == null) {
          workoutCommandReceived(chatId, userId);
        } else {
          requestInstructor(chatId, userId);
        }
      } else if (selectedWorkout.get(userId) == null) {
        workoutCommandReceived(chatId, userId);
      } else {
        String answer =
                "Выбрана пробная тренировка: <b>" + selectedWorkout.get(userId) + "</b>"
                        + (selectedInstructor.get(userId) != null ? "\nТренер: <b>" + selectedInstructor.get(userId) + "</b>" : "")
                        + "\nДля подтверждения записи укажите, пожалуйста, Ваши контактные данные в обратном сообщении. Например, <i>Имя Фамилия, телефон</i>."
                        + "\nИли нажмите на кнопку <b>Поделиться контактом</b> " + downArrow;
        sendMessage(chatId, answer, createResetButton(), createSubmitButton(), userId);
      }
    }
  }

  private void feedbackCommandReceived(Long chatId, Long userId) {
    String answer = "Напишите, пожалуйста, обратную связь в ответном сообщении.";
    sendMessage(chatId, answer, createResetButton(), userId);
  }

  private void sendFeedback(Long userId, String userName, String message) {
    sendMessage(botConfig.getAdminChatId(), "Получена обратная связь от @" + userName + "\n" + message, userId);
    lastCommand.remove(userId);
  }

  private void trialStepCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите действие:";
    sendMessage(chatId, answer, createTrialStepButtons(), userId);
  }
  private void instructorCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите тренера:";
    sendMessage(chatId, answer, createInstructorButtons(), userId);
  }
  private void requestInstructor(Long chatId, Long userId) {
    String answer = "Выбрать тренера?";
    sendMessage(chatId, answer, createRequestInstructorButtons(), userId);
  }
  private void workoutCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите класс:";
    sendMessage(chatId, answer, createWorkoutButtons(), userId);
  }

  private void defaultCommandReceived(Long chatId, Long userId) {
    String answer = "Выберите доступное действие:";
    sendMessage(chatId, answer, createStartButtons(), userId);
  }
  private void succesCommandReceived(Long chatId, Long userId) {
    String answer = "Спасибо, Ваша заявка зарегистрирована. Мы скоро свяжемся с Вами!";
    sendMessage(chatId, answer, userId);

    selectedWorkout.remove(userId);
    selectedInstructor.remove(userId);
    contact.remove(userId);
    lastCommand.remove(userId);
  }
  private void succesFeedbackReceived(Long chatId, Long userId) {
    String answer = "Спасибо за обратную связь!";
    sendMessage(chatId, answer, userId);

    selectedWorkout.remove(userId);
    selectedInstructor.remove(userId);
    contact.remove(userId);
    lastCommand.remove(userId);
  }

  private void sendAdminMessage(Long chatId, String message, Long userId) {
    String answer = "Новая запись на пробную тренировку: \n" + message;
    if (selectedInstructor.get(userId) != null) {
      answer += "\nТренер: " + selectedInstructor.get(userId);
    }
    if (selectedWorkout.get(userId) != null) {
      answer += "\nКласс: " + selectedWorkout.get(userId);
    }
    sendMessage(chatId, answer, userId);
  }

  private void sendMessage(Long chatId, String textToSend, Long userId) {
    sendMessage(chatId, textToSend, null, null, userId);
  }
  private void sendMessage(Long chatId, String textToSend, InlineKeyboardMarkup inlineKeyboardMarkup, Long userId) {
    sendMessage(chatId, textToSend, inlineKeyboardMarkup, null, userId);
  }
  private void sendMessage(Long chatId, String textToSend, ReplyKeyboardMarkup replyKeyboardMarkup, Long userId) {
    sendMessage(chatId, textToSend, null, replyKeyboardMarkup, userId);
  }
  private void sendMessage(Long chatId, String textToSend, InlineKeyboardMarkup inlineKeyboardMarkup, ReplyKeyboardMarkup replyKeyboardMarkup, Long userId) {
    SendMessage sendMessage = new SendMessage();
    sendMessage.enableHtml(true);
    sendMessage.setChatId(String.valueOf(chatId));
    sendMessage.setText(textToSend);
    try {
      if (inlineKeyboardMarkup != null) {
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
      } else if (replyKeyboardMarkup != null) {
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
      }
      Message sentMessage = execute(sendMessage);
      lastMessageId.put(userId, sentMessage.getMessageId());
    } catch (TelegramApiException e) {
      logger.error("Error sending message", e);
    }
  }

  private InlineKeyboardMarkup createStartButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    List<Button> buttons = new ArrayList<>();
    Button trial = new Button();
    trial.setDescription("Бесплатная пробная тренировка");
    trial.setCallBack("trial_message");
    buttons.add(trial);

    Button feedback = new Button();
    feedback.setDescription("Обратная связь");
    feedback.setCallBack("feedback_message");
    buttons.add(feedback);

    buttons.forEach(b -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(b.getDescription());
      trialButton.setCallbackData(b.getCallBack());
      rowInline.add(trialButton);
      // Set the keyboard to the markup
      rowsInline.add(rowInline);
    });
    // Add it to the message
    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private ReplyKeyboardMarkup createSubmitButton() {
    List<KeyboardButton> buttons = new ArrayList<>();
    KeyboardButton button = new KeyboardButton("Поделиться контактом");
    button.setRequestContact(true);
    buttons.add(button);
    List<KeyboardRow> rows = new ArrayList<>();
    rows.add(new KeyboardRow(buttons));
    ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();

    replyKeyboardMarkup.setSelective(true);
    replyKeyboardMarkup.setResizeKeyboard(true);
    replyKeyboardMarkup.setOneTimeKeyboard(true);

    replyKeyboardMarkup.setKeyboard(rows);

    return replyKeyboardMarkup;
  }

  private InlineKeyboardMarkup createTrialStepButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    HashMap<String, String> buttons = new HashMap<>();
    buttons.put("select_class_message", "Выбрать тренировку");
    buttons.put("select_instructor_message", "Выбрать тренера");
    buttons.keySet().forEach(button -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(buttons.get(button));
      trialButton.setCallbackData(button);
      rowInline.add(trialButton);
      rowsInline.add(rowInline);
    });

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }
  private InlineKeyboardMarkup createResetButton() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }
  private InlineKeyboardMarkup createInstructorButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    constants.getInstructors().forEach(instructor -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(instructor);
      trialButton.setCallbackData("instructor_message"+instructor);
      rowInline.add(trialButton);
      rowsInline.add(rowInline);
    });

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }
  private InlineKeyboardMarkup createRequestInstructorButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
    List<InlineKeyboardButton> rowInline = new ArrayList<>();

    InlineKeyboardButton yesButton = new InlineKeyboardButton();
    yesButton.setText("Да");
    yesButton.setCallbackData("select_instructor_message");
    rowInline.add(yesButton);

    InlineKeyboardButton noButton = new InlineKeyboardButton();
    noButton.setText("Нет");
    noButton.setCallbackData("skip_instructor_message");
    rowInline.add(noButton);

    rowsInline.add(rowInline);
    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private InlineKeyboardMarkup createWorkoutButtons() {
    InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
    List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

    IntStream.range(0, constants.getWorkouts().size()).forEach(index -> {
      List<InlineKeyboardButton> rowInline = new ArrayList<>();
      InlineKeyboardButton trialButton = new InlineKeyboardButton();
      trialButton.setText(constants.getWorkouts().get(index));
      trialButton.setCallbackData("workout_message"+index);
      rowInline.add(trialButton);
      rowsInline.add(rowInline);
    });

    rowsInline.add(addResetButton());

    markupInline.setKeyboard(rowsInline);

    return markupInline;
  }

  private List<InlineKeyboardButton> addResetButton() {
    List<InlineKeyboardButton> rowInline = new ArrayList<>();
    InlineKeyboardButton resetButton = new InlineKeyboardButton();
    resetButton.setText(leftArrow + " Назад");
    resetButton.setCallbackData("reset_trial_message");
    rowInline.add(resetButton);

    return rowInline;
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
