import SwiftUI

struct MainTabView: View {
    @EnvironmentObject var authViewModel: AuthViewModel
    @EnvironmentObject var dashboardViewModel: DashboardViewModel
    @EnvironmentObject var teamViewModel: TeamViewModel

    @State private var selectedTab = 0
    @State private var showAddMenu = false
    @State private var showAddShift = false
    @State private var showWeekPlan = false
    @State private var showProfile = false
    @State private var showInsights = false
    @State private var showJobsView = false
    @State private var editingShiftId: String?
    @Namespace private var tabNamespace

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch selectedTab {
                case 0:
                    DashboardView(
                        onEditShift: { id in editingShiftId = id; showAddShift = true },
                        onNavigateToProfile: { showProfile = true },
                        onNavigateToPay: { selectedTab = 2 }
                    )
                    .transition(.opacity)
                case 1:
                    PlanView(
                        onEditShift: { id in editingShiftId = id; showAddShift = true },
                        onAddShift: { editingShiftId = nil; showAddShift = true }
                    )
                    .transition(.opacity)
                case 2:
                    PayView()
                        .transition(.opacity)
                case 3:
                    TeamView()
                        .transition(.opacity)
                default:
                    EmptyView()
                }
            }
            .animation(.easeInOut(duration: 0.25), value: selectedTab)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .safeAreaInset(edge: .bottom) {
                HStack {
                    TabBarButton(icon: "house.fill", label: "Home", isSelected: selectedTab == 0, action: { selectedTab = 0 }, namespace: tabNamespace)
                    TabBarButton(icon: "calendar", label: "Plan", isSelected: selectedTab == 1, action: { selectedTab = 1 }, namespace: tabNamespace)
                    Spacer().frame(width: 72)
                    TabBarButton(icon: "dollarsign.circle.fill", label: "Pay", isSelected: selectedTab == 2, action: { selectedTab = 2 }, namespace: tabNamespace)
                    TabBarButton(icon: "person.3.fill", label: "Team", isSelected: selectedTab == 3, action: { selectedTab = 3 }, namespace: tabNamespace)
                }
                .padding(.horizontal, 8)
                .frame(height: 64)
                .background(
                    TabBarCutoutShape()
                        .fill(Color(UIColor.systemBackground))
                        .shadow(color: .black.opacity(0.08), radius: 8, y: -2)
                        .ignoresSafeArea(.container, edges: .bottom)
                )
            }

            // Backdrop scrim: dims the whole dashboard while the FAB menu is
            // open so background content can't bleed through the menu.
            if showAddMenu {
                Color.black.opacity(0.35)
                    .ignoresSafeArea()
                    .onTapGesture { showAddMenu = false }
                    .transition(.opacity)
            }

            // FAB docked in the tab bar cutout: the bar is 64pt tall above the
            // bottom safe area, so a 36pt bottom padding puts the 56pt FAB's
            // center right on the bar's top edge. The menu is centered above it.
            VStack(spacing: 14) {
                if showAddMenu {
                    fabActionMenu
                        .transition(.scale(scale: 0.9, anchor: .bottom).combined(with: .opacity))
                }

                Button(action: { showAddMenu.toggle() }) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .fill(
                                LinearGradient(
                                    colors: [.primaryGreen, Color(red: 0.106, green: 0.263, blue: 0.196)],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .frame(width: 56, height: 56)
                            .shadow(color: .black.opacity(0.2), radius: 8, y: 4)

                        Image(systemName: "plus")
                            .font(.system(size: 24, weight: .medium))
                            .foregroundColor(.white)
                            .rotationEffect(.degrees(showAddMenu ? 45 : 0))
                    }
                }
            }
            .padding(.bottom, 36)
        }
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: showAddMenu)
        .sheet(isPresented: $showAddShift) {
            NavigationStack {
                AddShiftView(shiftId: editingShiftId)
                    .environmentObject(dashboardViewModel)
            }
        }
        .sheet(isPresented: $showWeekPlan) {
            NavigationStack {
                WeekPlanView()
                    .environmentObject(dashboardViewModel)
            }
        }
        .sheet(isPresented: $showProfile) {
            ProfileView(
                onNavigateToInsights: {
                    showProfile = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showInsights = true
                    }
                },
                onNavigateToJobs: {
                    showProfile = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        showJobsView = true
                    }
                }
            )
            .environmentObject(authViewModel)
            .environmentObject(dashboardViewModel)
        }
        .sheet(isPresented: $showInsights) {
            InsightsView()
                .environmentObject(dashboardViewModel)
        }
        .sheet(isPresented: $showJobsView) {
            NavigationStack {
                JobsView()
                    .environmentObject(dashboardViewModel)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Done") { showJobsView = false }
                        }
                    }
            }
        }
    }

    // MARK: - FAB Action Menu

    private var fabActionMenu: some View {
        VStack(spacing: 0) {
            fabMenuRow(icon: "plus", title: "Add Single Shift") {
                showAddMenu = false
                editingShiftId = nil
                showAddShift = true
            }
            fabMenuRow(icon: "calendar.badge.plus", title: "Plan Entire Week") {
                showAddMenu = false
                showWeekPlan = true
            }
        }
        .padding(8)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(UIColor.systemBackground))
                .shadow(color: .black.opacity(0.15), radius: 12, y: 4)
        )
    }

    private func fabMenuRow(icon: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.primaryGreen.opacity(0.1))
                        .frame(width: 34, height: 34)
                    Image(systemName: icon)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.primaryGreen)
                }
                Text(title)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primary)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(minWidth: 220, alignment: .leading)
        }
        .buttonStyle(.plain)
    }
}

/// Bar outline with a smooth Bezier notch at the top center so the docked FAB
/// (56pt, centered on the bar's top edge) sits in a cradle with a ~6pt gap
/// instead of the bar cutting straight through it.
private struct TabBarCutoutShape: Shape {
    var cutoutRadius: CGFloat = 34 // FAB radius (28) + 6pt gap

    func path(in rect: CGRect) -> Path {
        let r = cutoutRadius
        let cx = rect.midX
        var p = Path()
        p.move(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: cx - r * 1.75, y: rect.minY))
        // Left shoulder eases in horizontally, dips to the notch floor…
        p.addCurve(
            to: CGPoint(x: cx, y: rect.minY + r),
            control1: CGPoint(x: cx - r * 0.9, y: rect.minY),
            control2: CGPoint(x: cx - r * 0.75, y: rect.minY + r)
        )
        // …and the right shoulder mirrors it back up to the top edge.
        p.addCurve(
            to: CGPoint(x: cx + r * 1.75, y: rect.minY),
            control1: CGPoint(x: cx + r * 0.75, y: rect.minY + r),
            control2: CGPoint(x: cx + r * 0.9, y: rect.minY)
        )
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}

private struct TabBarButton: View {
    let icon: String
    let label: String
    let isSelected: Bool
    let action: () -> Void
    var namespace: Namespace.ID? = nil

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                ZStack {
                    if isSelected, let ns = namespace {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.primaryGreen.opacity(0.1))
                            .matchedGeometryEffect(id: "tabIndicator", in: ns)
                    } else {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.clear)
                    }
                    Image(systemName: icon)
                        .font(.system(size: 20))
                        .scaleEffect(isSelected ? 1.1 : 1.0)
                }
                .frame(height: 28)
                .padding(.horizontal, 12)
                .padding(.vertical, 4)

                Text(label)
                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
            }
            .foregroundColor(isSelected ? .primaryGreen : .secondary)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isSelected)
        }
        .frame(maxWidth: .infinity)
    }
}
