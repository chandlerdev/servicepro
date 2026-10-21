import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../config/app_theme.dart';
import '../view_model/forgot_password_view_model.dart';

/// Forgot password: email → 6-digit code + new password → back to sign in.
class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _emailForm = GlobalKey<FormState>();
  final _resetForm = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _code = TextEditingController();
  final _password = TextEditingController();
  final _confirm = TextEditingController();
  bool _obscure = true;

  @override
  void dispose() {
    _email.dispose();
    _code.dispose();
    _password.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    final vm = context.read<ForgotPasswordViewModel>();
    if (vm.busy || !(_emailForm.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();
    await vm.sendCode(_email.text);
  }

  Future<void> _reset() async {
    final vm = context.read<ForgotPasswordViewModel>();
    if (vm.busy || !(_resetForm.currentState?.validate() ?? false)) return;
    FocusScope.of(context).unfocus();
    final ok = await vm.reset(_code.text, _password.text);
    if (!ok || !mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Password changed — sign in with your new password'),
      ),
    );
    context.pop();
  }

  @override
  Widget build(BuildContext context) {
    final vm = context.watch<ForgotPasswordViewModel>();
    return Scaffold(
      backgroundColor: AppColors.brandDark,
      appBar: AppBar(
        backgroundColor: AppColors.brandDark,
        foregroundColor: Colors.white,
        elevation: 0,
        title: const Text('Forgot password'),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: vm.codeSent ? _resetStep(vm) : _emailStep(vm),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _emailStep(ForgotPasswordViewModel vm) {
    return Form(
      key: _emailForm,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Reset your password',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          const Text(
            "Enter the email you sign in with. We'll email you a 6-digit code.",
            style: TextStyle(color: AppColors.muted),
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _email,
            enabled: !vm.busy,
            autofocus: true,
            keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email],
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => _sendCode(),
            decoration: const InputDecoration(
              labelText: 'Email',
              prefixIcon: Icon(Icons.mail_outline),
            ),
            validator: (v) {
              final s = v?.trim() ?? '';
              if (s.isEmpty) return 'Enter your email';
              if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(s)) {
                return 'Enter a valid email';
              }
              return null;
            },
          ),
          if (vm.error != null) _Banner(vm.error!, error: true),
          const SizedBox(height: 18),
          _SubmitButton(
            label: 'Send code',
            busy: vm.busy,
            onPressed: _sendCode,
          ),
        ],
      ),
    );
  }

  Widget _resetStep(ForgotPasswordViewModel vm) {
    return Form(
      key: _resetForm,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Enter the code',
            style: Theme.of(
              context,
            ).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 6),
          Text(
            'We sent a 6-digit code to ${vm.email}. '
            'It expires in 10 minutes.',
            style: const TextStyle(color: AppColors.muted),
          ),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: vm.busy ? null : vm.changeEmail,
              style: TextButton.styleFrom(padding: EdgeInsets.zero),
              child: const Text('Change email'),
            ),
          ),
          const SizedBox(height: 4),
          TextFormField(
            controller: _code,
            enabled: !vm.busy,
            autofocus: true,
            keyboardType: TextInputType.number,
            autofillHints: const [AutofillHints.oneTimeCode],
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 22, letterSpacing: 8),
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(labelText: '6-digit code'),
            validator: (v) => RegExp(r'^\d{6}$').hasMatch(v ?? '')
                ? null
                : 'Enter the 6-digit code from the email',
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _password,
            enabled: !vm.busy,
            obscureText: _obscure,
            autofillHints: const [AutofillHints.newPassword],
            textInputAction: TextInputAction.next,
            decoration: InputDecoration(
              labelText: 'New password',
              helperText: 'At least 8 characters',
              prefixIcon: const Icon(Icons.lock_outline),
              suffixIcon: IconButton(
                tooltip: _obscure ? 'Show' : 'Hide',
                icon: Icon(
                  _obscure
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                ),
                onPressed: () => setState(() => _obscure = !_obscure),
              ),
            ),
            validator: (v) {
              final s = v ?? '';
              if (s.length < 8) return 'At least 8 characters';
              if (s.length > 100) return 'At most 100 characters';
              return null;
            },
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _confirm,
            enabled: !vm.busy,
            obscureText: _obscure,
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => _reset(),
            decoration: const InputDecoration(
              labelText: 'Confirm new password',
              prefixIcon: Icon(Icons.lock_outline),
            ),
            validator: (v) =>
                v == _password.text ? null : "Passwords don't match",
          ),
          if (vm.error != null) _Banner(vm.error!, error: true),
          const SizedBox(height: 18),
          _SubmitButton(
            label: 'Set new password',
            busy: vm.busy,
            onPressed: _reset,
          ),
          const SizedBox(height: 8),
          Center(
            child: vm.cooldown > 0
                ? Text(
                    'Resend code in ${vm.cooldown}s',
                    style: const TextStyle(color: AppColors.muted),
                  )
                : TextButton(
                    onPressed: vm.busy ? null : vm.resend,
                    child: const Text('Resend code'),
                  ),
          ),
        ],
      ),
    );
  }
}

class _Banner extends StatelessWidget {
  const _Banner(this.text, {this.error = false});

  final String text;
  final bool error;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(top: 14),
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: error ? const Color(0xFFFDECEC) : AppColors.okBg,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: error ? const Color(0xFFF2C7C7) : AppColors.okBorder,
          ),
        ),
        child: Text(
          text,
          style: TextStyle(
            color: error ? AppColors.danger : AppColors.ok,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

class _SubmitButton extends StatelessWidget {
  const _SubmitButton({
    required this.label,
    required this.busy,
    required this.onPressed,
  });

  final String label;
  final bool busy;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return FilledButton(
      onPressed: busy ? null : onPressed,
      style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
      child: busy
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2.4,
                color: Colors.white,
              ),
            )
          : Text(label, style: const TextStyle(fontSize: 16)),
    );
  }
}
