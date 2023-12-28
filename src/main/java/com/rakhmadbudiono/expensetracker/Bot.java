package com.rakhmadbudiono.expensetracker;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.HashMap;

@Component
public class Bot extends TelegramLongPollingBot {
    private Map<Long, List<Expense>> expenseMap = new HashMap<>();
    private Map<Long, Expense> pendingExpensesMap = new HashMap<>();
    private String[] categories = { "Food", "Transportation", "Utilities", "Entertainment", "Other" };
    private String[] importances = { "Essentials", "Have to Have", "Nice to Have", "Shouldn't have" };

    static final String PREFIX_CATEGORY = "category:";
    static final String PREFIX_IMPORTANCE = "importance:";

    @Override
    public String getBotUsername() {
        return System.getenv("BOT_USERNAME");
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();

            String text = message.getText().trim();

            if (text.startsWith("/help")) {
                sendSimpleMessage(chatId, "Commands:\n" +
                        "/help - Show commands\n" +
                        "/input <amount> <description> - Record an expense\n" +
                        "/report <yyyy-mm-dd> - Generate report from given date");
            } else if (text.matches("/input \\d+\\s.+")) {
                processExpense(chatId, text);
            } else if (text.matches("/report \\d{4}-\\d{2}-\\d{2}")) {
                generateMonthlyReport(chatId, text);
            } else {
                sendSimpleMessage(chatId, "Unknown command.");
            }
        } else if (update.hasCallbackQuery()) {
            // Process callback queries, e.g., when the user selects a category or importance
            processCallbackQuery(update.getCallbackQuery());
        }
    }

    private void sendSimpleMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void processExpense(long chatId, String text) {
        String[] parts = text.split("\\s", 3);
        double amount = Double.parseDouble(parts[1]);
        String description = parts[2];

        pendingExpensesMap.put(chatId, new Expense(amount, description));

        sendListsKeyboard(chatId, categories, "category");

        Expense expense = pendingExpensesMap.get(chatId);

        if (expenseMap.containsKey(chatId)) {
            expenseMap.get(chatId).add(expense);
        } else {
            List<Expense> expenses = new ArrayList<>();
            expenses.add(expense);
            expenseMap.put(chatId, expenses);
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        if (data.startsWith(PREFIX_CATEGORY)) {
            String selectedCategory = data.substring(PREFIX_CATEGORY.length());
            pendingExpensesMap.get(chatId).setCategory(selectedCategory);

            sendListsKeyboard(chatId, importances, "importance");
        } else if (data.startsWith(PREFIX_IMPORTANCE)) {
            String selectedImportance = data.substring(PREFIX_IMPORTANCE.length());
            pendingExpensesMap.get(chatId).setImportance(selectedImportance);

            sendSimpleMessage(chatId, "Expense recorded: " + pendingExpensesMap.get(chatId).toCsvString());
            pendingExpensesMap.remove(chatId);
        }
    }

    private void sendListsKeyboard(long chatId, String[] list, String type) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Choose " + type);

        InlineKeyboardMarkup markupKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        for (String item : list) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(item);
            button.setCallbackData(type + ":" + item);
            rowInline.add(button);
            rowsInline.add(rowInline);
        }

        markupKeyboard.setKeyboard(rowsInline);
        message.setReplyMarkup(markupKeyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void generateMonthlyReport(long chatId, String text) {
        List<Expense> userExpenses = expenseMap.get(chatId);

        if (userExpenses.isEmpty()) {
            sendSimpleMessage(chatId, "No expenses to generate a report for.");
            return;
        }

        String[] parts = text.split(" ");
        if (parts.length != 2 || !parts[0].equals("/report")) {
            sendSimpleMessage(chatId, "Invalid command format. Please use /report YYYY-MM-DD");
            return;
        }

        String dateString = parts[1];
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date reportDate;
        try {
            reportDate = dateFormat.parse(dateString);
        } catch (ParseException e) {
            sendSimpleMessage(chatId, "Invalid date format. Please use YYYY-MM-DD");
            return;
        }

        StringBuilder csvContent = new StringBuilder("category,amount,importance,description\n");

        for (Expense expense : userExpenses) {
            if (expense.getDate().after(reportDate)) {
                csvContent.append(expense.toCsvString()).append("\n");
            }
        }

        if (csvContent.length() == 0) {
            sendSimpleMessage(chatId, "No expenses found for the specified date.");
            return;
        }

        String fileName = "monthly_report.csv";
        saveCsvToFile(csvContent.toString(), fileName);

        sendDocument(chatId, fileName);

        new File(fileName).delete();
    }

    private void saveCsvToFile(String csvContent, String fileName) {
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(csvContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendDocument(long chatId, String fileName) {
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(chatId);
        sendDocument.setDocument(new InputFile(new File(fileName)));

        try {
            execute(sendDocument);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}