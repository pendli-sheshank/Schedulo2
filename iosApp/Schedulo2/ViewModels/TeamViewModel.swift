import Foundation
import Combine
import FirebaseAuth
import FirebaseFirestore

struct TeamInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var name: String = ""
    var ownerId: String = ""
    var inviteCode: String = ""
    var createdAt: Int64 = 0
    var memberCount: Int = 0
}

struct TeamMemberInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var userId: String = ""
    var role: String = "member"
    var joinedAt: Int64 = 0
    var displayName: String = ""
    var email: String = ""
}

struct ShiftTaskInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var title: String = ""
    var isCompleted: Bool = false
}

struct TeamShiftInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var teamId: String = ""
    var assignedTo: String = ""
    var assignedBy: String = ""
    var company: String = ""
    var role: String = ""
    var startTime: Int64 = 0
    var endTime: Int64 = 0
    var hourlyRate: Double = 0.0
    var notes: String = ""
    var status: String = "assigned"
    var tasks: [ShiftTaskInfo] = []

    var durationHours: Double {
        guard endTime > startTime else { return 0.0 }
        return Double(endTime - startTime) / 3_600_000.0
    }

    var startDate: Date {
        Date(timeIntervalSince1970: Double(startTime) / 1000.0)
    }

    var endDate: Date {
        Date(timeIntervalSince1970: Double(endTime) / 1000.0)
    }
}

struct MemberJobInfo: Identifiable, Equatable {
    var id: String = UUID().uuidString
    var title: String = ""
    var defaultHourlyRate: Double = 15.0
    var isGigWork: Bool = false
}

final class TeamViewModel: ObservableObject {
    @Published var teams: [TeamInfo] = []
    @Published var currentTeam: TeamInfo?
    @Published var members: [TeamMemberInfo] = []
    @Published var teamShifts: [TeamShiftInfo] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var userRole: String = "member"
    @Published var memberJobs: [MemberJobInfo] = []

    private let db = Firestore.firestore(database: "schedulo2")
    private var teamsListener: ListenerRegistration?
    private var membersListener: ListenerRegistration?
    private var shiftsListener: ListenerRegistration?
    private var memberJobsListener: ListenerRegistration?

    var currentUserId: String? { Auth.auth().currentUser?.uid }
    var currentUserEmail: String? { Auth.auth().currentUser?.email }

    var isManager: Bool { userRole == "manager" }

    func fetchMemberJobs(userId: String) {
        memberJobsListener?.remove()
        memberJobsListener = db.collection("jobs")
            .whereField("userId", isEqualTo: userId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let documents = snapshot?.documents else {
                    self?.memberJobs = []
                    return
                }
                self?.memberJobs = documents.compactMap { doc in
                    let data = doc.data()
                    return MemberJobInfo(
                        id: doc.documentID,
                        title: data["title"] as? String ?? "",
                        defaultHourlyRate: data["defaultHourlyRate"] as? Double ?? 15.0,
                        isGigWork: data["isGigWork"] as? Bool ?? false
                    )
                }
            }
    }

    func loadTeams() {
        guard let uid = currentUserId else { return }
        teamsListener?.remove()

        teamsListener = db.collection("team_members")
            .whereField("userId", isEqualTo: uid)
            .addSnapshotListener { [weak self] snapshot, error in
                guard let docs = snapshot?.documents, error == nil else { return }
                let teamIds = docs.compactMap { $0.data()["teamId"] as? String }
                guard !teamIds.isEmpty else {
                    self?.teams = []
                    return
                }
                self?.fetchTeams(ids: teamIds)
            }
    }

    private func fetchTeams(ids: [String]) {
        let group = DispatchGroup()
        var fetched: [TeamInfo] = []

        for id in ids {
            group.enter()
            db.collection("teams").document(id).getDocument { snapshot, _ in
                defer { group.leave() }
                guard let data = snapshot?.data() else { return }
                let team = TeamInfo(
                    id: snapshot?.documentID ?? id,
                    name: data["name"] as? String ?? "",
                    ownerId: data["ownerId"] as? String ?? "",
                    inviteCode: data["inviteCode"] as? String ?? "",
                    createdAt: data["createdAt"] as? Int64 ?? 0,
                    memberCount: data["memberCount"] as? Int ?? 0
                )
                fetched.append(team)
            }
        }

        group.notify(queue: .main) { [weak self] in
            self?.teams = fetched.sorted { $0.name < $1.name }
            if self?.currentTeam == nil, let first = fetched.first {
                self?.selectTeam(first)
            }
        }
    }

    func selectTeam(_ team: TeamInfo) {
        currentTeam = team
        loadMembers(teamId: team.id)
        loadTeamShifts(teamId: team.id)
        updateUserRole(teamId: team.id)
    }

    private func updateUserRole(teamId: String) {
        guard let uid = currentUserId else { return }
        db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments { [weak self] snapshot, _ in
                if let doc = snapshot?.documents.first {
                    self?.userRole = doc.data()["role"] as? String ?? "member"
                }
            }
    }

    func loadMembers(teamId: String) {
        membersListener?.remove()
        membersListener = db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.members = docs.map { doc in
                    let data = doc.data()
                    return TeamMemberInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        userId: data["userId"] as? String ?? "",
                        role: data["role"] as? String ?? "member",
                        joinedAt: data["joinedAt"] as? Int64 ?? 0,
                        displayName: data["displayName"] as? String ?? "",
                        email: data["email"] as? String ?? ""
                    )
                }.sorted { $0.displayName < $1.displayName }
            }
    }

    func loadTeamShifts(teamId: String) {
        shiftsListener?.remove()
        shiftsListener = db.collection("team_shifts")
            .whereField("teamId", isEqualTo: teamId)
            .addSnapshotListener { [weak self] snapshot, _ in
                guard let docs = snapshot?.documents else { return }
                self?.teamShifts = docs.map { doc in
                    let data = doc.data()
                    let tasksRaw = data["tasks"] as? [[String: Any]] ?? []
                    let parsedTasks = tasksRaw.map { t in
                        ShiftTaskInfo(
                            id: t["id"] as? String ?? UUID().uuidString,
                            title: t["title"] as? String ?? "",
                            isCompleted: t["isCompleted"] as? Bool ?? false
                        )
                    }
                    return TeamShiftInfo(
                        id: doc.documentID,
                        teamId: data["teamId"] as? String ?? "",
                        assignedTo: data["assignedTo"] as? String ?? "",
                        assignedBy: data["assignedBy"] as? String ?? "",
                        company: data["company"] as? String ?? "",
                        role: data["role"] as? String ?? "",
                        startTime: (data["startTime"] as? NSNumber)?.int64Value ?? 0,
                        endTime: (data["endTime"] as? NSNumber)?.int64Value ?? 0,
                        hourlyRate: data["hourlyRate"] as? Double ?? 0.0,
                        notes: data["notes"] as? String ?? "",
                        status: data["status"] as? String ?? "assigned",
                        tasks: parsedTasks
                    )
                }.sorted { $0.startTime > $1.startTime }
            }
    }

    // MARK: - Team CRUD

    func createTeam(name: String) {
        guard let uid = currentUserId else { return }
        isLoading = true
        let teamId = UUID().uuidString
        let inviteCode = generateInviteCode()

        let teamData: [String: Any] = [
            "name": name,
            "ownerId": uid,
            "inviteCode": inviteCode,
            "createdAt": Int64(Date().timeIntervalSince1970 * 1000),
            "memberCount": 1
        ]

        let memberData: [String: Any] = [
            "teamId": teamId,
            "userId": uid,
            "role": "manager",
            "joinedAt": Int64(Date().timeIntervalSince1970 * 1000),
            "displayName": currentUserEmail ?? "",
            "email": currentUserEmail ?? ""
        ]

        let batch = db.batch()
        batch.setData(teamData, forDocument: db.collection("teams").document(teamId))
        batch.setData(memberData, forDocument: db.collection("team_members").document(UUID().uuidString))

        batch.commit { [weak self] error in
            DispatchQueue.main.async {
                self?.isLoading = false
                if let error = error {
                    self?.errorMessage = error.localizedDescription
                } else {
                    self?.loadTeams()
                }
            }
        }
    }

    func joinTeam(inviteCode: String) {
        guard let uid = currentUserId else { return }
        isLoading = true

        db.collection("teams")
            .whereField("inviteCode", isEqualTo: inviteCode.uppercased())
            .getDocuments { [weak self] snapshot, error in
                guard let doc = snapshot?.documents.first else {
                    DispatchQueue.main.async {
                        self?.isLoading = false
                        self?.errorMessage = "Invalid invite code"
                    }
                    return
                }

                let teamId = doc.documentID

                self?.db.collection("team_members")
                    .whereField("teamId", isEqualTo: teamId)
                    .whereField("userId", isEqualTo: uid)
                    .getDocuments { existingSnapshot, _ in
                        if let existingDocs = existingSnapshot?.documents, !existingDocs.isEmpty {
                            DispatchQueue.main.async {
                                self?.isLoading = false
                                self?.errorMessage = "You are already a member of this team."
                            }
                            return
                        }

                        let memberData: [String: Any] = [
                            "teamId": teamId,
                            "userId": uid,
                            "role": "member",
                            "joinedAt": Int64(Date().timeIntervalSince1970 * 1000),
                            "displayName": self?.currentUserEmail ?? "",
                            "email": self?.currentUserEmail ?? ""
                        ]

                        let batch = self?.db.batch()
                        batch?.setData(memberData, forDocument: self?.db.collection("team_members").document(UUID().uuidString) ?? self!.db.collection("team_members").document())
                        batch?.updateData(["memberCount": FieldValue.increment(Int64(1))], forDocument: self?.db.collection("teams").document(teamId) ?? self!.db.collection("teams").document(teamId))

                        batch?.commit { error in
                            DispatchQueue.main.async {
                                self?.isLoading = false
                                if let error = error {
                                    self?.errorMessage = error.localizedDescription
                                } else {
                                    self?.loadTeams()
                                }
                            }
                        }
                    }
            }
    }

    func assignShift(to memberId: String, company: String, role: String, startTime: Int64, endTime: Int64, hourlyRate: Double, notes: String, tasks: [ShiftTaskInfo] = []) {
        guard let teamId = currentTeam?.id, let uid = currentUserId else { return }
        let shiftId = UUID().uuidString

        let tasksData = tasks.map { t -> [String: Any] in
            ["id": t.id, "title": t.title, "isCompleted": t.isCompleted]
        }

        let data: [String: Any] = [
            "teamId": teamId,
            "assignedTo": memberId,
            "assignedBy": uid,
            "company": company,
            "role": role,
            "startTime": startTime,
            "endTime": endTime,
            "hourlyRate": hourlyRate,
            "notes": notes,
            "status": "assigned",
            "tasks": tasksData
        ]

        db.collection("team_shifts").document(shiftId).setData(data) { [weak self] error in
            if error == nil {
                let teamShift = TeamShiftInfo(
                    id: shiftId,
                    teamId: teamId,
                    assignedTo: memberId,
                    assignedBy: uid,
                    company: company,
                    role: role,
                    startTime: startTime,
                    endTime: endTime,
                    hourlyRate: hourlyRate,
                    notes: notes,
                    status: "assigned",
                    tasks: tasks
                )
                self?.createPersonalShiftFromTeam(teamShift, targetUserId: memberId)
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func toggleTaskCompletion(shiftId: String, taskId: String) {
        let ref = db.collection("team_shifts").document(shiftId)
        ref.getDocument { [weak self] snapshot, _ in
            guard let data = snapshot?.data(),
                  var tasks = data["tasks"] as? [[String: Any]] else { return }
            if let idx = tasks.firstIndex(where: { $0["id"] as? String == taskId }) {
                let current = tasks[idx]["isCompleted"] as? Bool ?? false
                tasks[idx]["isCompleted"] = !current
                if let localIdx = self?.teamShifts.firstIndex(where: { $0.id == shiftId }),
                   let taskIdx = self?.teamShifts[localIdx].tasks.firstIndex(where: { $0.id == taskId }) {
                    DispatchQueue.main.async {
                        self?.teamShifts[localIdx].tasks[taskIdx].isCompleted = !current
                    }
                }
                ref.updateData(["tasks": tasks]) { error in
                    if let error = error {
                        DispatchQueue.main.async {
                            self?.errorMessage = error.localizedDescription
                            if let localIdx = self?.teamShifts.firstIndex(where: { $0.id == shiftId }),
                               let taskIdx = self?.teamShifts[localIdx].tasks.firstIndex(where: { $0.id == taskId }) {
                                self?.teamShifts[localIdx].tasks[taskIdx].isCompleted = current
                            }
                        }
                    }
                }
            }
        }
    }

    private func createPersonalShiftFromTeam(_ teamShift: TeamShiftInfo, targetUserId: String) {
        let shiftData: [String: Any] = [
            "userId": targetUserId,
            "company": teamShift.company,
            "role": teamShift.role,
            "startTime": teamShift.startTime,
            "endTime": teamShift.endTime,
            "hourlyRate": teamShift.hourlyRate,
            "isGig": false,
            "customEarned": 0.0,
            "reminderBeforeMinutes": 30,
            "isPaid": false,
            "notes": "Team shift: \(teamShift.notes)".trimmingCharacters(in: .whitespaces),
            "bonusApplied": false,
            "bonusAmount": 0.0,
            "teamShiftId": teamShift.id
        ]
        db.collection("shifts").document(UUID().uuidString).setData(shiftData) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.errorMessage = error.localizedDescription
                }
            }
        }
    }

    func updateTeamName(teamId: String, newName: String) {
        db.collection("teams").document(teamId).updateData(["name": newName]) { [weak self] error in
            if error == nil {
                DispatchQueue.main.async {
                    self?.loadTeams()
                }
            } else {
                DispatchQueue.main.async {
                    self?.errorMessage = error?.localizedDescription
                }
            }
        }
    }

    func deleteTeam(teamId: String) {
        isLoading = true
        let group = DispatchGroup()
        var deleteError: Error?

        group.enter()
        db.collection("team_members").whereField("teamId", isEqualTo: teamId).getDocuments { [weak self] snapshot, _ in
            if let docs = snapshot?.documents {
                let batch = self?.db.batch()
                docs.forEach { batch?.deleteDocument($0.reference) }
                batch?.commit { error in
                    if let error = error { deleteError = error }
                    group.leave()
                }
            } else {
                group.leave()
            }
        }

        group.enter()
        db.collection("team_shifts").whereField("teamId", isEqualTo: teamId).getDocuments { [weak self] snapshot, _ in
            if let docs = snapshot?.documents {
                let batch = self?.db.batch()
                docs.forEach { batch?.deleteDocument($0.reference) }
                batch?.commit { error in
                    if let error = error { deleteError = error }
                    group.leave()
                }
            } else {
                group.leave()
            }
        }

        group.notify(queue: .main) { [weak self] in
            if let deleteError = deleteError {
                self?.isLoading = false
                self?.errorMessage = deleteError.localizedDescription
                return
            }
            self?.db.collection("teams").document(teamId).delete { error in
                DispatchQueue.main.async {
                    self?.isLoading = false
                    if let error = error {
                        self?.errorMessage = error.localizedDescription
                        return
                    }
                    self?.currentTeam = nil
                    self?.members = []
                    self?.teamShifts = []
                    self?.loadTeams()
                }
            }
        }
    }

    func deleteTeamShift(shiftId: String) {
        db.collection("team_shifts").document(shiftId).delete()
    }

    func promoteMember(memberDocId: String) {
        let previous = members
        if let idx = members.firstIndex(where: { $0.id == memberDocId }) {
            members[idx].role = "manager"
        }
        db.collection("team_members").document(memberDocId).updateData(["role": "manager"]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to promote member: \(error.localizedDescription)"
                }
            }
        }
    }

    func demoteMember(memberDocId: String) {
        let previous = members
        if let idx = members.firstIndex(where: { $0.id == memberDocId }) {
            members[idx].role = "member"
        }
        db.collection("team_members").document(memberDocId).updateData(["role": "member"]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to demote member: \(error.localizedDescription)"
                }
            }
        }
    }

    func removeMember(memberDocId: String, teamId: String) {
        let previous = members
        members.removeAll { $0.id == memberDocId }

        let batch = db.batch()
        batch.deleteDocument(db.collection("team_members").document(memberDocId))
        batch.updateData(["memberCount": FieldValue.increment(Int64(-1))], forDocument: db.collection("teams").document(teamId))

        batch.commit { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.members = previous
                    self?.errorMessage = "Failed to remove member: \(error.localizedDescription)"
                }
            }
        }
    }

    func updateShiftStatus(shiftId: String, newStatus: String) {
        let previous = teamShifts
        if let idx = teamShifts.firstIndex(where: { $0.id == shiftId }) {
            teamShifts[idx].status = newStatus
        }
        db.collection("team_shifts").document(shiftId).updateData(["status": newStatus]) { [weak self] error in
            if let error = error {
                DispatchQueue.main.async {
                    self?.teamShifts = previous
                    self?.errorMessage = "Failed to update shift status: \(error.localizedDescription)"
                }
            }
        }
    }

    func leaveTeam(teamId: String) {
        guard let uid = currentUserId else { return }
        db.collection("team_members")
            .whereField("teamId", isEqualTo: teamId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments { [weak self] snapshot, _ in
                guard let doc = snapshot?.documents.first else { return }
                let batch = self?.db.batch()
                batch?.deleteDocument(doc.reference)
                batch?.updateData(["memberCount": FieldValue.increment(Int64(-1))], forDocument: self?.db.collection("teams").document(teamId) ?? self!.db.collection("teams").document(teamId))
                batch?.commit { error in
                    DispatchQueue.main.async {
                        if let error = error {
                            self?.errorMessage = error.localizedDescription
                            return
                        }
                        self?.currentTeam = nil
                        self?.loadTeams()
                    }
                }
            }
    }

    func removeAllListeners() {
        teamsListener?.remove()
        membersListener?.remove()
        shiftsListener?.remove()
        memberJobsListener?.remove()
    }

    private func generateInviteCode() -> String {
        let chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return String((0..<6).map { _ in chars.randomElement()! })
    }
}
