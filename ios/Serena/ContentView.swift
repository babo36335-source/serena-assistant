import SwiftUI
import AVFoundation

struct ChatMessageItem: Identifiable, Equatable {
    let id = UUID()
    let role: String // "user" or "model"
    var content: String
    let timestamp: Date = Date()
    var isError: Bool = false
}

struct ContentView: View {
    @State private var messages: [ChatMessageItem] = [
        ChatMessageItem(role: "model", content: "안녕하세요! 당신만의 다정하고 스마트한 비서 세레나(Serena)예요 💜 오늘 어떤 이야기를 나누고 싶으신가요?")
    ]
    @State private var inputPrompt: String = ""
    @State private var isGenerating: Bool = false
    @State private var apiKey: String = ""
    @State private var showSettings: Bool = false
    
    private let speechSynthesizer = AVSpeechSynthesizer()

    var body: some View {
        NavigationStack {
            ZStack {
                Color(red: 0.98, green: 0.97, blue: 1.0)
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    // Header Bar
                    HStack {
                        HStack(spacing: 10) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [Color.purple, Color(red: 0.75, green: 0.55, blue: 0.98)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 40, height: 40)
                                Text("✨")
                                    .font(.system(size: 20))
                            }
                            VStack(alignment: .leading, spacing: 2) {
                                Text("세레나 (Serena)")
                                    .font(.system(size: 17, weight: .bold))
                                    .foregroundColor(Color(red: 0.2, green: 0.1, blue: 0.35))
                                Text(isGenerating ? "생각하는 중... 💜" : "대화 가능")
                                    .font(.system(size: 12))
                                    .foregroundColor(isGenerating ? Color.purple : Color.secondary)
                            }
                        }

                        Spacer()

                        Button(action: { showSettings.toggle() }) {
                            Image(systemName: "gearshape.fill")
                                .font(.system(size: 18))
                                .foregroundColor(Color.purple)
                                .padding(8)
                                .background(Color.purple.opacity(0.1))
                                .clipShape(Circle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color.white)
                    .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 2)

                    // Message List
                    ScrollViewReader { proxy in
                        ScrollView {
                            LazyVStack(spacing: 14) {
                                ForEach(messages) { msg in
                                    MessageBubbleView(message: msg, onSpeak: {
                                        speak(text: msg.content)
                                    })
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                        }
                        .onChange(of: messages.count) { _ in
                            if let last = messages.last {
                                withAnimation {
                                    proxy.scrollTo(last.id, anchor: .bottom)
                                }
                            }
                        }
                    }

                    // Bottom Input Area
                    VStack(spacing: 6) {
                        HStack(spacing: 10) {
                            TextField("세레나에게 편하게 이야기해 주세요...", text: $inputPrompt)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                                .background(Color(red: 0.95, green: 0.93, blue: 0.98))
                                .cornerRadius(22)

                            Button(action: sendMessage) {
                                Image(systemName: "arrow.up.circle.fill")
                                    .font(.system(size: 34))
                                    .foregroundColor(inputPrompt.trimmingCharacters(in: .whitespaces).isEmpty || isGenerating ? Color.gray.opacity(0.5) : Color.purple)
                            }
                            .disabled(inputPrompt.trimmingCharacters(in: .whitespaces).isEmpty || isGenerating)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                    }
                    .background(Color.white)
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showSettings) {
                SettingsView(apiKey: $apiKey)
            }
        }
    }

    private func sendMessage() {
        let prompt = inputPrompt.trimmingCharacters(in: .whitespaces)
        guard !prompt.isEmpty && !isGenerating else { return }

        inputPrompt = ""
        let userMsg = ChatMessageItem(role: "user", content: prompt)
        messages.append(userMsg)
        isGenerating = true

        let currentKey = apiKey.trimmingCharacters(in: .whitespaces)
        let model = "gemini-3.8-flash"
        let urlStr = "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=\(currentKey)"

        guard let url = URL(string: urlStr) else {
            messages.append(ChatMessageItem(role: "model", content: "URL 형식이 잘못되었습니다.", isError: true))
            isGenerating = false
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let personaInstruction = "당신은 사용자의 다정하고 귀여운 AI 친구이자 비서 '세레나(Serena)'입니다. 언제나 부드럽고 상냥한 한국어로 존댓말을 쓰며, 하트나 반짝이 이모지를 자연스럽게 섞어 대답해 주세요."

        let body: [String: Any] = [
            "systemInstruction": [
                "parts": [["text": personaInstruction]]
            ],
            "contents": [
                ["role": "user", "parts": [["text": prompt]]]
            ]
        ]

        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                self.isGenerating = false
                if let error = error {
                    self.messages.append(ChatMessageItem(role: "model", content: "네트워크 오류가 발생했습니다: \(error.localizedDescription)", isError: true))
                    return
                }

                guard let data = data else {
                    self.messages.append(ChatMessageItem(role: "model", content: "응답을 받지 못했습니다.", isError: true))
                    return
                }

                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    if let candidates = json["candidates"] as? [[String: Any]],
                       let first = candidates.first,
                       let content = first["content"] as? [String: Any],
                       let parts = content["parts"] as? [[String: Any]],
                       let text = parts.first?["text"] as? String {
                        self.messages.append(ChatMessageItem(role: "model", content: text))
                    } else if let errorObj = json["error"] as? [String: Any],
                              let msg = errorObj["message"] as? String {
                        self.messages.append(ChatMessageItem(role: "model", content: "API 오류: \(msg)", isError: true))
                    } else {
                        self.messages.append(ChatMessageItem(role: "model", content: "세레나가 답변을 생성하지 못했습니다. 설정(⚙️)에서 API 키를 확인해 주세요!", isError: true))
                    }
                }
            }
        }.resume()
    }

    private func speak(text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "ko-KR")
        utterance.rate = 0.5
        utterance.pitchMultiplier = 1.15
        speechSynthesizer.stopSpeaking(at: .immediate)
        speechSynthesizer.speak(utterance)
    }
}

struct MessageBubbleView: View {
    let message: ChatMessageItem
    let onSpeak: () -> Void

    var isUser: Bool { message.role == "user" }

    var body: some View {
        HStack(alignment: .bottom, spacing: 8) {
            if isUser { Spacer(minLength: 40) }

            VStack(alignment: isUser ? .trailing : .leading, spacing: 4) {
                Text(message.content)
                    .font(.system(size: 15))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .foregroundColor(isUser ? .white : (message.isError ? .red : Color(red: 0.15, green: 0.1, blue: 0.2)))
                    .background(
                        isUser ? Color.purple : (message.isError ? Color.red.opacity(0.12) : Color.white)
                    )
                    .cornerRadius(18)
                    .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)

                if !isUser && !message.isError {
                    Button(action: onSpeak) {
                        HStack(spacing: 4) {
                            Image(systemName: "speaker.wave.2.fill")
                            Text("듣기")
                        }
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color.purple)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.purple.opacity(0.1))
                        .cornerRadius(10)
                    }
                }
            }

            if !isUser { Spacer(minLength: 40) }
        }
    }
}

struct SettingsView: View {
    @Binding var apiKey: String
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Gemini API 키 설정")) {
                    SecureField("Google AI Studio API Key", text: $apiKey)
                    Text("Google AI Studio에서 발급받은 API 키를 입력해 주세요.")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
            }
            .navigationTitle("설정 ⚙️")
            .navigationBarItems(trailing: Button("완료") {
                dismiss()
            })
        }
    }
}
