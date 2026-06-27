import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class AppBackButton extends StatelessWidget {
  const AppBackButton({
    super.key,
    required this.fallbackLocation,
  });

  final String fallbackLocation;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: '返回',
      icon: const Icon(Icons.arrow_back),
      onPressed: () => _goBack(context),
    );
  }

  void _goBack(BuildContext context) {
    if (context.canPop()) {
      context.pop();
      return;
    }
    context.go(fallbackLocation);
  }
}
