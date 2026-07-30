Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

function ConvertTo-Rpn {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Expression
    )

    $output = New-Object System.Collections.Generic.List[object]
    $operators = New-Object System.Collections.Generic.Stack[string]
    $index = 0
    $expectValue = $true
    $validFunctions = @('sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'sqrt', 'log', 'ln', 'exp')

    $precedence = @{
        '^'  = 4
        'u-' = 3
        '*'  = 2
        '/'  = 2
        '%'  = 2
        '+'  = 1
        '-'  = 1
    }

    while ($index -lt $Expression.Length) {
        $ch = $Expression[$index]

        if ([char]::IsWhiteSpace($ch)) {
            $index++
            continue
        }

        if ([char]::IsDigit($ch) -or $ch -eq '.') {
            $start = $index
            $dotCount = 0

            while ($index -lt $Expression.Length -and ([char]::IsDigit($Expression[$index]) -or $Expression[$index] -eq '.')) {
                if ($Expression[$index] -eq '.') {
                    $dotCount++
                }

                if ($dotCount -gt 1) {
                    throw "Invalid number."
                }

                $index++
            }

            $numberText = $Expression.Substring($start, $index - $start)
            $number = 0.0

            if (-not [double]::TryParse(
                $numberText,
                [System.Globalization.NumberStyles]::Float,
                [System.Globalization.CultureInfo]::InvariantCulture,
                [ref]$number
            )) {
                throw "Invalid number."
            }

            $output.Add($number)
            $expectValue = $false
            continue
        }

        if ([char]::IsLetter($ch)) {
            $start = $index

            while ($index -lt $Expression.Length -and [char]::IsLetter($Expression[$index])) {
                $index++
            }

            $identifier = $Expression.Substring($start, $index - $start).ToLowerInvariant()

            if ($identifier -eq 'pi') {
                $output.Add([Math]::PI)
                $expectValue = $false
                continue
            }

            if ($identifier -eq 'e') {
                $output.Add([Math]::E)
                $expectValue = $false
                continue
            }

            if ($validFunctions -contains $identifier) {
                $operators.Push("fn:$identifier")
                $expectValue = $true
                continue
            }

            throw "Unknown identifier '$identifier'."
        }

        if ($ch -eq '(') {
            $operators.Push('(')
            $expectValue = $true
            $index++
            continue
        }

        if ($ch -eq ')') {
            while ($operators.Count -gt 0 -and $operators.Peek() -ne '(') {
                $output.Add($operators.Pop())
            }

            if ($operators.Count -eq 0) {
                throw "Mismatched parentheses."
            }

            [void]$operators.Pop()

            if ($operators.Count -gt 0 -and ([string]$operators.Peek()).StartsWith('fn:')) {
                $output.Add($operators.Pop())
            }

            $expectValue = $false
            $index++
            continue
        }

        if ('+-*/%^'.Contains([string]$ch)) {
            $op = [string]$ch

            if ($expectValue) {
                if ($op -eq '-') {
                    $op = 'u-'
                } else {
                    throw "Missing number."
                }
            }

            if ($op -eq 'u-') {
                $operators.Push($op)
                $expectValue = $true
                $index++
                continue
            }

            $rightAssociative = $op -eq '^'

            while ($operators.Count -gt 0 -and $operators.Peek() -ne '(') {
                $top = $operators.Peek()

                if (-not $precedence.ContainsKey($top)) {
                    break
                }

                $shouldPop = if ($rightAssociative) {
                    $precedence[$op] -lt $precedence[$top]
                } else {
                    $precedence[$op] -le $precedence[$top]
                }

                if (-not $shouldPop) {
                    break
                }

                $output.Add($operators.Pop())
            }

            $operators.Push($op)
            $expectValue = $true
            $index++
            continue
        }

        throw "Unsupported character '$ch'."
    }

    if ($expectValue -and $output.Count -gt 0) {
        throw "Expression cannot end with an operator."
    }

    while ($operators.Count -gt 0) {
        $op = $operators.Pop()

        if ($op -eq '(') {
            throw "Mismatched parentheses."
        }

        $output.Add($op)
    }

    return $output
}

function ConvertTo-Radians {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value,

        [Parameter(Mandatory = $true)]
        [ValidateSet('Degrees', 'Radians')]
        [string]$AngleMode
    )

    if ($AngleMode -eq 'Degrees') {
        return $Value * [Math]::PI / 180
    }

    return $Value
}

function ConvertFrom-Radians {
    param(
        [Parameter(Mandatory = $true)]
        [double]$Value,

        [Parameter(Mandatory = $true)]
        [ValidateSet('Degrees', 'Radians')]
        [string]$AngleMode
    )

    if ($AngleMode -eq 'Degrees') {
        return $Value * 180 / [Math]::PI
    }

    return $Value
}

function Invoke-CalculatorExpression {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Expression,

        [ValidateSet('Degrees', 'Radians')]
        [string]$AngleMode = 'Degrees'
    )

    if ([string]::IsNullOrWhiteSpace($Expression)) {
        return ''
    }

    $rpn = ConvertTo-Rpn -Expression $Expression
    $stack = New-Object System.Collections.Generic.Stack[double]

    foreach ($token in $rpn) {
        if ($token -is [double]) {
            $stack.Push($token)
            continue
        }

        $tokenText = [string]$token

        if ($tokenText -eq 'u-') {
            if ($stack.Count -lt 1) {
                throw "Missing number."
            }

            $stack.Push(-1 * $stack.Pop())
            continue
        }

        if ($tokenText.StartsWith('fn:')) {
            if ($stack.Count -lt 1) {
                throw "Missing number."
            }

            $value = $stack.Pop()
            $functionName = $tokenText.Substring(3)

            switch ($functionName) {
                'sin' { $stack.Push([Math]::Sin((ConvertTo-Radians -Value $value -AngleMode $AngleMode))) }
                'cos' { $stack.Push([Math]::Cos((ConvertTo-Radians -Value $value -AngleMode $AngleMode))) }
                'tan' { $stack.Push([Math]::Tan((ConvertTo-Radians -Value $value -AngleMode $AngleMode))) }
                'asin' {
                    if ($value -lt -1 -or $value -gt 1) {
                        throw "Input must be between -1 and 1."
                    }

                    $stack.Push((ConvertFrom-Radians -Value ([Math]::Asin($value)) -AngleMode $AngleMode))
                }
                'acos' {
                    if ($value -lt -1 -or $value -gt 1) {
                        throw "Input must be between -1 and 1."
                    }

                    $stack.Push((ConvertFrom-Radians -Value ([Math]::Acos($value)) -AngleMode $AngleMode))
                }
                'atan' { $stack.Push((ConvertFrom-Radians -Value ([Math]::Atan($value)) -AngleMode $AngleMode)) }
                'sqrt' {
                    if ($value -lt 0) {
                        throw "Cannot take the square root of a negative number."
                    }

                    $stack.Push([Math]::Sqrt($value))
                }
                'log' {
                    if ($value -le 0) {
                        throw "Log input must be greater than zero."
                    }

                    $stack.Push([Math]::Log10($value))
                }
                'ln' {
                    if ($value -le 0) {
                        throw "Log input must be greater than zero."
                    }

                    $stack.Push([Math]::Log($value))
                }
                'exp' { $stack.Push([Math]::Exp($value)) }
                default { throw "Unknown function." }
            }

            continue
        }

        if ($stack.Count -lt 2) {
            throw "Missing number."
        }

        $right = $stack.Pop()
        $left = $stack.Pop()

        switch ($token) {
            '+' { $stack.Push($left + $right) }
            '-' { $stack.Push($left - $right) }
            '*' { $stack.Push($left * $right) }
            '^' { $stack.Push([Math]::Pow($left, $right)) }
            '/' {
                if ($right -eq 0) {
                    throw "Cannot divide by zero."
                }

                $stack.Push($left / $right)
            }
            '%' {
                if ($right -eq 0) {
                    throw "Cannot divide by zero."
                }

                $stack.Push($left % $right)
            }
            default { throw "Unknown operator." }
        }
    }

    if ($stack.Count -ne 1) {
        throw "Invalid expression."
    }

    $result = $stack.Pop()

    if ([Math]::Abs($result) -lt 1e-12) {
        $result = 0
    }

    if ([double]::IsNaN($result) -or [double]::IsInfinity($result)) {
        throw "Invalid result."
    }

    return $result.ToString('G15', [System.Globalization.CultureInfo]::InvariantCulture)
}

function Add-CalculatorButton {
    param(
        [Parameter(Mandatory = $true)]
        [System.Windows.Forms.TableLayoutPanel]$Grid,

        [Parameter(Mandatory = $true)]
        [string]$Text,

        [Parameter(Mandatory = $true)]
        [int]$Column,

        [Parameter(Mandatory = $true)]
        [int]$Row,

        [Parameter(Mandatory = $true)]
        [scriptblock]$OnClick,

        [int]$ColumnSpan = 1,
        [System.Drawing.Color]$BackColor = [System.Drawing.Color]::FromArgb(35, 38, 46),
        [System.Drawing.Color]$ForeColor = [System.Drawing.Color]::FromArgb(241, 245, 249)
    )

    $button = New-Object System.Windows.Forms.Button
    $button.Text = $Text
    $button.Dock = [System.Windows.Forms.DockStyle]::Fill
    $button.Margin = New-Object System.Windows.Forms.Padding(4)
    $button.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
    $button.FlatAppearance.BorderColor = [System.Drawing.Color]::FromArgb(64, 68, 78)
    $button.FlatAppearance.MouseOverBackColor = [System.Drawing.Color]::FromArgb(58, 64, 78)
    $button.FlatAppearance.MouseDownBackColor = [System.Drawing.Color]::FromArgb(75, 85, 99)
    $button.BackColor = $BackColor
    $button.ForeColor = $ForeColor
    $button.Font = New-Object System.Drawing.Font('Segoe UI', 13, [System.Drawing.FontStyle]::Regular)
    $button.Add_Click($OnClick)

    $Grid.Controls.Add($button, $Column, $Row)

    if ($ColumnSpan -gt 1) {
        $Grid.SetColumnSpan($button, $ColumnSpan)
    }

    return $button
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'PowerShell Scientific Calculator'
$form.StartPosition = [System.Windows.Forms.FormStartPosition]::CenterScreen
$form.ClientSize = New-Object System.Drawing.Size(520, 620)
$form.MinimumSize = New-Object System.Drawing.Size(520, 620)
$form.BackColor = [System.Drawing.Color]::FromArgb(16, 17, 20)
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)
$form.KeyPreview = $true

$display = New-Object System.Windows.Forms.TextBox
$display.Dock = [System.Windows.Forms.DockStyle]::Top
$display.Height = 78
$display.Margin = New-Object System.Windows.Forms.Padding(12)
$display.TextAlign = [System.Windows.Forms.HorizontalAlignment]::Right
$display.Font = New-Object System.Drawing.Font('Segoe UI', 24, [System.Drawing.FontStyle]::Regular)
$display.BorderStyle = [System.Windows.Forms.BorderStyle]::FixedSingle
$display.BackColor = [System.Drawing.Color]::FromArgb(24, 26, 32)
$display.ForeColor = [System.Drawing.Color]::FromArgb(248, 250, 252)
$display.ReadOnly = $true

$grid = New-Object System.Windows.Forms.TableLayoutPanel
$grid.Dock = [System.Windows.Forms.DockStyle]::Fill
$grid.Padding = New-Object System.Windows.Forms.Padding(8)
$grid.BackColor = [System.Drawing.Color]::FromArgb(16, 17, 20)
$grid.ColumnCount = 5
$grid.RowCount = 7

for ($i = 0; $i -lt 5; $i++) {
    [void]$grid.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 20)))
}

for ($i = 0; $i -lt 7; $i++) {
    [void]$grid.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Percent, 14.2857)))
}

$state = @{
    AngleMode = 'Degrees'
}

$appendValue = {
    param([string]$Value)

    if ($display.Text -eq 'Error') {
        $display.Clear()
    }

    $display.Text += $Value
    $display.SelectionStart = $display.Text.Length
}

$clearDisplay = {
    $display.Clear()
}

$backspace = {
    if ($display.Text.Length -gt 0 -and $display.Text -ne 'Error') {
        $display.Text = $display.Text.Substring(0, $display.Text.Length - 1)
        $display.SelectionStart = $display.Text.Length
    }
}

$calculate = {
    try {
        $result = Invoke-CalculatorExpression -Expression $display.Text -AngleMode $state.AngleMode
        $display.Text = [string]$result
    } catch {
        $display.Text = 'Error'
    }

    $display.SelectionStart = $display.Text.Length
}

$numberColor = [System.Drawing.Color]::FromArgb(35, 38, 46)
$operatorColor = [System.Drawing.Color]::FromArgb(52, 58, 70)
$functionColor = [System.Drawing.Color]::FromArgb(31, 54, 59)
$constantColor = [System.Drawing.Color]::FromArgb(58, 48, 72)
$modeColor = [System.Drawing.Color]::FromArgb(66, 73, 88)
$equalsColor = [System.Drawing.Color]::FromArgb(37, 99, 235)
$dangerColor = [System.Drawing.Color]::FromArgb(92, 38, 48)

$angleModeButton = Add-CalculatorButton -Grid $grid -Text 'DEG' -Column 0 -Row 0 -BackColor $modeColor -OnClick {
    if ($state.AngleMode -eq 'Degrees') {
        $state.AngleMode = 'Radians'
        $angleModeButton.Text = 'RAD'
    } else {
        $state.AngleMode = 'Degrees'
        $angleModeButton.Text = 'DEG'
    }
}

Add-CalculatorButton -Grid $grid -Text 'C' -Column 1 -Row 0 -OnClick $clearDisplay -BackColor $dangerColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '(' -Column 2 -Row 0 -OnClick { & $appendValue '(' } -BackColor $operatorColor | Out-Null
Add-CalculatorButton -Grid $grid -Text ')' -Column 3 -Row 0 -OnClick { & $appendValue ')' } -BackColor $operatorColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'Back' -Column 4 -Row 0 -OnClick $backspace -BackColor $operatorColor | Out-Null

Add-CalculatorButton -Grid $grid -Text 'sin' -Column 0 -Row 1 -OnClick { & $appendValue 'sin(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'cos' -Column 1 -Row 1 -OnClick { & $appendValue 'cos(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'tan' -Column 2 -Row 1 -OnClick { & $appendValue 'tan(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'sqrt' -Column 3 -Row 1 -OnClick { & $appendValue 'sqrt(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '^' -Column 4 -Row 1 -OnClick { & $appendValue '^' } -BackColor $operatorColor | Out-Null

Add-CalculatorButton -Grid $grid -Text 'asin' -Column 0 -Row 2 -OnClick { & $appendValue 'asin(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'acos' -Column 1 -Row 2 -OnClick { & $appendValue 'acos(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'atan' -Column 2 -Row 2 -OnClick { & $appendValue 'atan(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'log' -Column 3 -Row 2 -OnClick { & $appendValue 'log(' } -BackColor $functionColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'ln' -Column 4 -Row 2 -OnClick { & $appendValue 'ln(' } -BackColor $functionColor | Out-Null

Add-CalculatorButton -Grid $grid -Text '7' -Column 0 -Row 3 -OnClick { & $appendValue '7' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '8' -Column 1 -Row 3 -OnClick { & $appendValue '8' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '9' -Column 2 -Row 3 -OnClick { & $appendValue '9' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '/' -Column 3 -Row 3 -OnClick { & $appendValue '/' } -BackColor $operatorColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '*' -Column 4 -Row 3 -OnClick { & $appendValue '*' } -BackColor $operatorColor | Out-Null

Add-CalculatorButton -Grid $grid -Text '4' -Column 0 -Row 4 -OnClick { & $appendValue '4' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '5' -Column 1 -Row 4 -OnClick { & $appendValue '5' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '6' -Column 2 -Row 4 -OnClick { & $appendValue '6' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '-' -Column 3 -Row 4 -OnClick { & $appendValue '-' } -BackColor $operatorColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '+' -Column 4 -Row 4 -OnClick { & $appendValue '+' } -BackColor $operatorColor | Out-Null

Add-CalculatorButton -Grid $grid -Text '1' -Column 0 -Row 5 -OnClick { & $appendValue '1' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '2' -Column 1 -Row 5 -OnClick { & $appendValue '2' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '3' -Column 2 -Row 5 -OnClick { & $appendValue '3' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '%' -Column 3 -Row 5 -OnClick { & $appendValue '%' } -BackColor $operatorColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'exp' -Column 4 -Row 5 -OnClick { & $appendValue 'exp(' } -BackColor $functionColor | Out-Null

Add-CalculatorButton -Grid $grid -Text '0' -Column 0 -Row 6 -OnClick { & $appendValue '0' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '.' -Column 1 -Row 6 -OnClick { & $appendValue '.' } -BackColor $numberColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'pi' -Column 2 -Row 6 -OnClick { & $appendValue 'pi' } -BackColor $constantColor | Out-Null
Add-CalculatorButton -Grid $grid -Text 'e' -Column 3 -Row 6 -OnClick { & $appendValue 'e' } -BackColor $constantColor | Out-Null
Add-CalculatorButton -Grid $grid -Text '=' -Column 4 -Row 6 -OnClick $calculate -BackColor $equalsColor -ForeColor ([System.Drawing.Color]::White) | Out-Null

$form.AcceptButton = $null
$form.Controls.Add($grid)
$form.Controls.Add($display)

$form.Add_KeyDown({
    param($sender, $event)

    if ($event.KeyCode -eq [System.Windows.Forms.Keys]::Enter) {
        & $calculate
        $event.SuppressKeyPress = $true
        return
    }

    if ($event.KeyCode -eq [System.Windows.Forms.Keys]::Escape) {
        & $clearDisplay
        $event.SuppressKeyPress = $true
        return
    }

    if ($event.KeyCode -eq [System.Windows.Forms.Keys]::Back) {
        & $backspace
        $event.SuppressKeyPress = $true
    }
})

[void][System.Windows.Forms.Application]::Run($form)
