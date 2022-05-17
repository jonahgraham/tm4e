/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.tm4e.core.internal.grammar;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.TMException;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.OptionalStandardTokenType;
import org.eclipse.tm4e.core.internal.theme.IThemeProvider;
import org.eclipse.tm4e.core.internal.theme.ThemeTrieElementRule;
import org.eclipse.tm4e.core.internal.utils.RegexSource;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/9157c7f869219dbaf9a5a5607f099c00fe694a29/src/grammar.ts#L320">
 *      github.com/Microsoft/vscode-textmate/blob/master/src/grammar.ts</a>
 */
final class BasicScopeAttributesProvider {

	private static final BasicScopeAttributes NULL_SCOPE_METADATA = new BasicScopeAttributes("", 0, 0, null);

	private static final Pattern STANDARD_TOKEN_TYPE_REGEXP = Pattern.compile("\\b(comment|string|regex)\\b");

	private final int initialLanguage;
	private final IThemeProvider themeProvider;
	private final Map<String, @Nullable BasicScopeAttributes> cache = new HashMap<>();

	private BasicScopeAttributes defaultMetaData;

	private final Map<String, Integer> embeddedLanguages = new HashMap<>();

	@Nullable
	private Pattern embeddedLanguagesRegex;

	BasicScopeAttributesProvider(final int initialLanguage, final IThemeProvider themeProvider,
		@Nullable final Map<String, Integer> embeddedLanguages) {
		this.initialLanguage = initialLanguage;
		this.themeProvider = themeProvider;
		this.defaultMetaData = new BasicScopeAttributes(
			"",
			this.initialLanguage,
			OptionalStandardTokenType.NotSet,
			List.of(this.themeProvider.getDefaults()));

		// embeddedLanguages handling
		if (embeddedLanguages != null) {
			// If embeddedLanguages are configured, fill in `this.embeddedLanguages`
			this.embeddedLanguages.putAll(embeddedLanguages);
		}

		// create the regex
		final var escapedScopes = this.embeddedLanguages.keySet().stream()
			.map(RegexSource::escapeRegExpCharacters)
			.collect(Collectors.toList());
		if (escapedScopes.isEmpty()) {
			// no scopes registered
			this.embeddedLanguagesRegex = null;
		} else {
			this.embeddedLanguagesRegex = Pattern.compile("^(("
				+ escapedScopes.stream().sorted(Collections.reverseOrder()).collect(Collectors.joining(")|("))
				+ "))($|\\.)");
		}
	}

	void onDidChangeTheme() {
		this.cache.clear();
		this.defaultMetaData = new BasicScopeAttributes(
			"",
			this.initialLanguage,
			OptionalStandardTokenType.NotSet,
			List.of(this.themeProvider.getDefaults()));
	}

	BasicScopeAttributes getDefaultMetadata() {
		return this.defaultMetaData;
	}

	BasicScopeAttributes getMetadataForScope(@Nullable final String scopeName) {
		if (scopeName == null) {
			return BasicScopeAttributesProvider.NULL_SCOPE_METADATA;
		}
		var value = this.cache.get(scopeName);
		if (value != null) {
			return value;
		}
		value = this.doGetMetadataForScope(scopeName);
		this.cache.put(scopeName, value);
		return value;
	}

	private BasicScopeAttributes doGetMetadataForScope(final String scopeName) {
		final int languageId = this.scopeToLanguage(scopeName);
		final int standardTokenType = BasicScopeAttributesProvider._toStandardTokenType(scopeName);
		final List<ThemeTrieElementRule> themeData = this.themeProvider.themeMatch(scopeName);

		return new BasicScopeAttributes(scopeName, languageId, standardTokenType, themeData);
	}

	/**
	 * Given a produced TM scope, return the language that token describes or null if unknown.
	 * e.g. source.html => html, source.css.embedded.html => css, punctuation.definition.tag.html => null
	 */
	private int scopeToLanguage(@Nullable final String scope) {
		if (scope == null) {
			return 0;
		}

		final var embeddedLanguagesRegex = this.embeddedLanguagesRegex;
		if (embeddedLanguagesRegex == null) {
			// no scopes registered
			return 0;
		}

		final var m = embeddedLanguagesRegex.matcher(scope);
		if (!m.find()) {
			// no scopes matched
			return 0;
		}

		return embeddedLanguages.getOrDefault(m.group(1), 0);
	}

	private static int /*OptionalStandardTokenType*/ _toStandardTokenType(final String scopeName) {
		final var m = STANDARD_TOKEN_TYPE_REGEXP.matcher(scopeName);
		if (!m.find()) {
			return OptionalStandardTokenType.NotSet;
		}
		final String group = m.group(1);
		return switch (group) {
		case "comment" -> OptionalStandardTokenType.Comment;
		case "string" -> OptionalStandardTokenType.String;
		case "regex" -> OptionalStandardTokenType.RegEx;
		case "meta.embedded" -> OptionalStandardTokenType.Other;
		default -> throw new TMException("Unexpected match for standard token type: " + group);
		};
	}
}