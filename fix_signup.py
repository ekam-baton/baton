import sys

file_path = 'app/src/main/kotlin/com/ekam/baton/ui/auth/SignupScreen.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if 'lineHeight = 20.sp' in line:
        break

tail = '''                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeLegalTitle = null
                        activeLegalContent = null
                    }
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            containerColor = Color(0xFF0F1623), // BatonSurface
            shape = RoundedCornerShape(24.dp)
        )
    }
}
'''

with open(file_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
    f.write(tail)
